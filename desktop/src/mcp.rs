//! MCP server (streamable HTTP, JSON responses) on 127.0.0.1 so AI clients can
//! load, inspect and simulate circuits. Tool calls are forwarded to the page's
//! window.__mcp (shim.js), which answers through the `mcp_reply` command.

use serde_json::{json, Value};
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{mpsc, Mutex};
use std::time::Duration;
use tauri::{AppHandle, Manager};

type Reply = Result<String, String>;

static ENABLED: AtomicBool = AtomicBool::new(false);
static STARTED: AtomicBool = AtomicBool::new(false);

/// Options > Enable MCP Server. Off by default: while on, any local program can drive the simulator.
// ponytail: turning it off refuses calls (403) but keeps the port bound until the app exits.
#[tauri::command]
pub fn mcp_set_enabled(app: AppHandle, enabled: bool) -> Result<(), String> {
    ENABLED.store(enabled, Ordering::Relaxed);
    if enabled && !STARTED.swap(true, Ordering::Relaxed) {
        let port = port();
        if let Err(e) = start(app, port) {
            STARTED.store(false, Ordering::Relaxed);
            ENABLED.store(false, Ordering::Relaxed);
            return Err(format!("MCP server could not start on port {port}: {e}"));
        }
    }
    Ok(())
}

fn port() -> u16 {
    std::env::var("CIRCUITJS_MCP_PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(7432)
}

#[derive(Default)]
pub struct Pending(Mutex<HashMap<u64, mpsc::Sender<Reply>>>);

#[tauri::command]
pub fn mcp_reply(state: tauri::State<Pending>, id: u64, ok: bool, result: String) {
    if let Some(tx) = state.0.lock().unwrap().remove(&id) {
        let _ = tx.send(if ok { Ok(result) } else { Err(result) });
    }
}

const INSTRUCTIONS: &str = "CircuitJS circuit simulator (the desktop app must be running). \
Circuits use the CircuitJS text format: a '$ ...' options line, then one element per line \
('<type> x1 y1 x2 y2 flags params...'). Easiest workflow: list_examples / load_example a similar \
circuit, get_circuit, edit the text, load_circuit. Element indexes (for probes) come from \
list_elements and are 0-based. A voltage probe on a wire gives that node's voltage to ground; \
on a part it gives the voltage across the part. simulate() resets and runs a transient analysis \
from t=0, then returns sampled waveforms plus min/max/avg/rms/frequency.";

fn tools() -> Value {
    json!([
        {"name": "list_examples", "description": "List the built-in example circuits (menu path and file name).",
         "inputSchema": {"type": "object", "properties": {}}},
        {"name": "load_example", "description": "Load a built-in example circuit by file name (from list_examples). Returns its elements.",
         "inputSchema": {"type": "object", "properties": {"file": {"type": "string"}}, "required": ["file"]}},
        {"name": "get_circuit", "description": "Get the current circuit as CircuitJS text (what a .csim file contains).",
         "inputSchema": {"type": "object", "properties": {}}},
        {"name": "load_circuit", "description": "Replace the current circuit with CircuitJS text. Returns its elements.",
         "inputSchema": {"type": "object", "properties": {"text": {"type": "string"}, "name": {"type": "string"}}, "required": ["text"]}},
        {"name": "list_elements", "description": "List elements with index, type, dump line and live readings (voltage, current, etc.).",
         "inputSchema": {"type": "object", "properties": {}}},
        {"name": "simulate", "description": "Reset and run a transient simulation to stop_time seconds, recording the probes. Returns per-probe stats (min, max, avg, rms, final, frequency_hz) and a waveform resampled to `points` points.",
         "inputSchema": {"type": "object", "properties": {
            "stop_time": {"type": "number", "description": "seconds, e.g. 0.01"},
            "probes": {"type": "array", "items": {"type": "object", "properties": {
                "element": {"type": "integer"},
                "quantity": {"type": "string", "enum": ["voltage", "current"]}}, "required": ["element"]}},
            "points": {"type": "integer", "description": "waveform points per probe, default 100, 0 = stats only"}},
          "required": ["stop_time", "probes"]}},
        {"name": "set_running", "description": "Pause or resume the live simulation.",
         "inputSchema": {"type": "object", "properties": {"running": {"type": "boolean"}}, "required": ["running"]}}
    ])
}

fn start(app: AppHandle, port: u16) -> Result<(), String> {
    let server = tiny_http::Server::http(("127.0.0.1", port)).map_err(|e| e.to_string())?;
    std::thread::spawn(move || {
        for req in server.incoming_requests() {
            let app = app.clone();
            // own thread per request: a long simulate() must not block other calls
            std::thread::spawn(move || handle(&app, req));
        }
    });
    Ok(())
}

fn respond(req: tiny_http::Request, status: u16, body: Option<Value>) {
    let mut r = tiny_http::Response::from_string(body.map(|b| b.to_string()).unwrap_or_default())
        .with_status_code(status);
    if status == 200 {
        r.add_header(
            tiny_http::Header::from_bytes("Content-Type", "application/json; charset=utf-8")
                .unwrap(),
        );
    }
    let _ = req.respond(r);
}

fn handle(app: &AppHandle, mut req: tiny_http::Request) {
    // DNS-rebinding guard: only local pages (or non-browser clients) may call us
    let bad_origin = req.headers().iter().any(|h| {
        h.field.equiv("Origin") && {
            let o = h.value.as_str();
            !(o.starts_with("http://127.0.0.1") || o.starts_with("http://localhost"))
        }
    });
    if bad_origin || !ENABLED.load(Ordering::Relaxed) {
        return respond(req, 403, None);
    }
    if req.url() != "/mcp" {
        return respond(req, 404, None);
    }
    if *req.method() != tiny_http::Method::Post {
        return respond(req, 405, None);
    }
    let mut body = String::new();
    let parsed = req
        .as_reader()
        .read_to_string(&mut body)
        .ok()
        .and_then(|_| serde_json::from_str::<Value>(&body).ok());
    let Some(msg) = parsed else {
        let err = json!({"jsonrpc": "2.0", "id": null, "error": {"code": -32700, "message": "parse error"}});
        return respond(req, 200, Some(err));
    };
    match answer(&msg, |tool, args| call_page(app, tool, args)) {
        Some(out) => respond(req, 200, Some(out)),
        None => respond(req, 202, None), // notification
    }
}

/// JSON-RPC reply to one MCP message, None for notifications. `call` runs a tool.
fn answer(msg: &Value, call: impl FnOnce(&str, &Value) -> Reply) -> Option<Value> {
    let id = msg.get("id")?.clone();
    let params = &msg["params"];
    let result = match msg["method"].as_str().unwrap_or("") {
        "initialize" => Ok(json!({
            "protocolVersion": params["protocolVersion"].as_str().unwrap_or("2025-06-18"),
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "circuitjs", "version": env!("CARGO_PKG_VERSION")},
            "instructions": INSTRUCTIONS
        })),
        "ping" => Ok(json!({})),
        "tools/list" => Ok(json!({"tools": tools()})),
        "tools/call" => Ok(
            match call(params["name"].as_str().unwrap_or(""), &params["arguments"]) {
                Ok(text) => json!({"content": [{"type": "text", "text": text}]}),
                Err(e) => json!({"content": [{"type": "text", "text": e}], "isError": true}),
            },
        ),
        m => Err(json!({"code": -32601, "message": format!("method not found: {m}")})),
    };
    Some(match result {
        Ok(r) => json!({"jsonrpc": "2.0", "id": id, "result": r}),
        Err(e) => json!({"jsonrpc": "2.0", "id": id, "error": e}),
    })
}

/// `circuitjs.exe --mcp-stdio`: stdio MCP server for clients that launch servers themselves
/// (Claude Desktop). Answers handshake and tool list itself, so it connects even while the app
/// is closed; tool calls go to the running app's HTTP server.
pub fn stdio_bridge() {
    use std::io::{BufRead, Write};
    for line in std::io::stdin().lock().lines() {
        let Ok(line) = line else { break };
        let Ok(msg) = serde_json::from_str::<Value>(&line) else {
            continue;
        };
        // own thread per message: a long simulate() must not block ping etc.
        std::thread::spawn(move || {
            let out = if msg["method"] == "tools/call" {
                // pass the app's reply through; if the app is off, a tool error the model can read
                Some(
                    forward(&line)
                        .unwrap_or_else(|e| answer(&msg, |_, _| Err(e)).unwrap().to_string()),
                )
            } else {
                answer(&msg, |_, _| unreachable!()).map(|v| v.to_string())
            };
            if let Some(out) = out {
                let mut so = std::io::stdout().lock();
                let _ = writeln!(so, "{}", out.trim_end());
                let _ = so.flush();
            }
        });
    }
}

/// POST one JSON-RPC message to the running app and return its raw reply body.
fn forward(body: &str) -> Reply {
    use std::io::{Read, Write};
    let port = port();
    let mut s = std::net::TcpStream::connect(("127.0.0.1", port)).map_err(|_| {
        "CircuitJS is not running. Start it and turn on Options > Enable MCP Server.".to_string()
    })?;
    write!(
        s,
        "POST /mcp HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nContent-Type: application/json\r\n\
         Content-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )
    .map_err(|e| e.to_string())?;
    let mut resp = String::new();
    s.read_to_string(&mut resp).map_err(|e| e.to_string())?;
    let (head, body) = resp
        .split_once("\r\n\r\n")
        .ok_or("bad reply from CircuitJS")?;
    match head.split(' ').nth(1) {
        Some("200") => Ok(body.to_string()),
        Some("403") => {
            Err("The MCP server is off in CircuitJS. Turn on Options > Enable MCP Server.".into())
        }
        code => Err(format!("CircuitJS replied HTTP {}", code.unwrap_or("?"))),
    }
}

fn call_page(app: &AppHandle, tool: &str, args: &Value) -> Reply {
    static NEXT: AtomicU64 = AtomicU64::new(1);
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    let win = app
        .get_webview_window("main")
        .ok_or("CircuitJS window is closed")?;
    let (tx, rx) = mpsc::channel();
    let pending = app.state::<Pending>();
    pending.0.lock().unwrap().insert(id, tx);
    let args = if args.is_null() {
        json!({})
    } else {
        args.clone()
    };
    let js = format!("window.__mcp({id}, {}, {})", json!(tool), args);
    let reply = match win.eval(&js) {
        Ok(()) => rx
            .recv_timeout(Duration::from_secs(600))
            .unwrap_or_else(|_| Err("timed out waiting for the simulator".into())),
        Err(e) => Err(e.to_string()),
    };
    pending.0.lock().unwrap().remove(&id);
    reply
}
