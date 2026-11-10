#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::{WebviewUrl, WebviewWindow, WebviewWindowBuilder};

const SHIM: &str = include_str!("shim.js");

#[tauri::command]
async fn save_dialog() -> Option<String> {
    rfd::FileDialog::new()
        .add_filter("Circuit", &["txt"])
        .save_file()
        .map(|p| p.display().to_string())
}

#[tauri::command]
async fn write_file(path: String, text: String) -> Result<(), String> {
    std::fs::write(path, text).map_err(|e| e.to_string())
}

#[tauri::command]
async fn open_file() -> Result<Option<(String, String)>, String> {
    let Some(p) = rfd::FileDialog::new()
        .add_filter("Circuit", &["txt"])
        .add_filter("All files", &["*"])
        .pick_file()
    else {
        return Ok(None);
    };
    let text = std::fs::read_to_string(&p).map_err(|e| e.to_string())?;
    Ok(Some((p.display().to_string(), text)))
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
        .invoke_handler(tauri::generate_handler![save_dialog, write_file, open_file, toggle_devtools])
        .setup(|app| {
            WebviewWindowBuilder::new(app, "main", WebviewUrl::App("circuitjs.html".into()))
                .title("CircuitJS")
                .inner_size(1280.0, 800.0)
                .initialization_script(SHIM)
                .build()?;
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running CircuitJS");
}
