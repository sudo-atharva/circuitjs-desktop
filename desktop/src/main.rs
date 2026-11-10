#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod mcp;

use std::path::Path;
use std::sync::atomic::{AtomicUsize, Ordering};
use tauri::webview::NewWindowResponse;
use tauri::{WebviewUrl, WebviewWindow, WebviewWindowBuilder};

const SHIM: &str = concat!(include_str!("shim.js"), include_str!("mcp.js"));

fn read(p: &Path) -> Result<(String, String), String> {
    let text = std::fs::read_to_string(p).map_err(|e| format!("{}: {e}", p.display()))?;
    Ok((p.display().to_string(), text))
}

#[tauri::command]
async fn save_dialog(ext: String) -> Option<String> {
    let name = if ext == "csim" { "CircuitJS circuit" } else { "File" };
    rfd::FileDialog::new()
        .add_filter(name, &[ext.as_str()])
        .save_file()
        .map(|p| p.display().to_string())
}

#[tauri::command]
async fn write_file(path: String, text: String) -> Result<(), String> {
    std::fs::write(path, text).map_err(|e| e.to_string())
}

#[tauri::command]
async fn open_file() -> Result<Option<(String, String)>, String> {
    match rfd::FileDialog::new()
        .add_filter("CircuitJS circuit", &["csim", "txt"])
        .add_filter("All files", &["*"])
        .pick_file()
    {
        Some(p) => read(&p).map(Some),
        None => Ok(None),
    }
}

// file passed on the command line, e.g. double-clicking a .csim
#[tauri::command]
fn startup_file() -> Result<Option<(String, String)>, String> {
    match std::env::args().nth(1) {
        Some(a) => read(Path::new(&a)).map(Some),
        None => Ok(None),
    }
}

#[tauri::command]
fn toggle_devtools(w: WebviewWindow) {
    if w.is_devtools_open() {
        w.close_devtools()
    } else {
        w.open_devtools()
    }
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            save_dialog,
            write_file,
            open_file,
            startup_file,
            toggle_devtools,
            mcp::mcp_reply
        ])
        .manage(mcp::Pending::default())
        .setup(|app| {
            let handle = app.handle().clone();
            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("circuitjs.html".into()))
                .title("CircuitJS")
                .inner_size(1280.0, 800.0)
                .initialization_script(SHIM)
                // waveform viewer + "New Window" use window.open. Build the popup as a Tauri
                // window so it can load app pages and still reach window.opener's data.
                .on_new_window(move |_url, features| {
                    static N: AtomicUsize = AtomicUsize::new(0);
                    let label = format!("popup{}", N.fetch_add(1, Ordering::Relaxed));
                    let built = WebviewWindowBuilder::new(
                        &handle,
                        label,
                        WebviewUrl::External("about:blank".parse().unwrap()),
                    )
                    .window_features(features)
                    .title("CircuitJS")
                    .on_document_title_changed(|w, title| {
                        let _ = w.set_title(&title);
                    })
                    .build();
                    match built {
                        Ok(window) => NewWindowResponse::Create { window },
                        Err(_) => NewWindowResponse::Deny,
                    }
                })
                .build()?;
            let port = std::env::var("CIRCUITJS_MCP_PORT")
                .ok()
                .and_then(|p| p.parse().ok())
                .unwrap_or(7432);
            mcp::start(app.handle().clone(), port);
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running CircuitJS");
}
