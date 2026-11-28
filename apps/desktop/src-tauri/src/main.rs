#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
    process::{Child, Command, Stdio},
    sync::Mutex,
    time::Duration,
};

use rand::{rngs::OsRng, RngCore};
use reqwest::{Client, Method};
use serde::Serialize;
use serde_json::Value;
use tauri::{AppHandle, Manager, State};

const API_BASE_URL: &str = "http://127.0.0.1:17890";

struct PosApiState {
    client: Client,
    token: String,
    child: Mutex<Option<Child>>,
    startup_error: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ApiStatus {
    available: bool,
    message: String,
}

fn generate_local_token() -> String {
    let mut bytes = [0_u8; 32];
    OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}

fn start_local_api(app: &AppHandle, token: &str) -> Result<Child, String> {
    let resource_dir = app.path().resource_dir().map_err(|error| error.to_string())?;
    let jar_path = resource_dir.join("restaurant-pos-local-api.jar");

    if !jar_path.is_file() {
        return Err("The packaged local API JAR is missing from the application resources.".to_string());
    }

    let java_binary = std::env::var("POS_JAVA_BIN").unwrap_or_else(|_| "java".to_string());
    Command::new(java_binary)
        .arg("-jar")
        .arg(jar_path)
        .env("POS_BIND_ADDRESS", "127.0.0.1")
        .env("POS_LOCAL_API_PORT", "17890")
        .env("POS_LOCAL_TOKEN", token)
        .env("POS_DB_URL", std::env::var("POS_DB_URL").unwrap_or_else(|_| {
            "jdbc:postgresql://127.0.0.1:5432/restaurant_pos".to_string()
        }))
        .env("POS_DB_USERNAME", std::env::var("POS_DB_USERNAME").unwrap_or_else(|_| "restaurant_pos".to_string()))
        .env("POS_DB_PASSWORD", std::env::var("POS_DB_PASSWORD").unwrap_or_default())
        .env("POS_CURRENCY", std::env::var("POS_CURRENCY").unwrap_or_else(|_| "ILS".to_string()))
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|error| format!("Could not start the local API process: {error}"))
}

#[tauri::command]
async fn api_status(state: State<'_, PosApiState>) -> ApiStatus {
    if let Some(error) = &state.startup_error {
        return ApiStatus {
            available: false,
            message: error.clone(),
        };
    }

    match state.client
        .get(format!("{API_BASE_URL}/actuator/health"))
        .timeout(Duration::from_secs(2))
        .send()
        .await
    {
        Ok(response) if response.status().is_success() => ApiStatus {
            available: true,
            message: "Local API is ready".to_string(),
        },
        Ok(response) => ApiStatus {
            available: false,
            message: format!("Local API health check returned HTTP {}", response.status()),
        },
        Err(_) => ApiStatus {
            available: false,
            message: "Local API is starting or the local database is unavailable".to_string(),
        },
    }
}

#[tauri::command]
async fn pos_api_request(
    state: State<'_, PosApiState>,
    method: String,
    path: String,
    body: Option<Value>,
    idempotency_key: Option<String>,
) -> Result<Value, String> {
    if !path.starts_with("/api/v1/")
        || path.contains("..")
        || path.contains("://")
        || path.contains('#')
    {
        return Err("Only relative /api/v1/ endpoints are allowed.".to_string());
    }

    let method = match method.to_ascii_uppercase().as_str() {
        "GET" => Method::GET,
        "POST" => Method::POST,
        "PUT" => Method::PUT,
        "PATCH" => Method::PATCH,
        "DELETE" => Method::DELETE,
        _ => return Err("Unsupported local API method.".to_string()),
    };

    let mut request = state.client
        .request(method, format!("{API_BASE_URL}{path}"))
        .header("X-POS-Local-Token", &state.token)
        .header("Accept", "application/json")
        .timeout(Duration::from_secs(15));

    if let Some(key) = idempotency_key {
        if key.trim().is_empty() || key.len() > 128 {
            return Err("Idempotency-Key must contain 1 to 128 characters.".to_string());
        }
        request = request.header("Idempotency-Key", key);
    }

    if let Some(json) = body {
        request = request.json(&json);
    }

    let response = request.send().await.map_err(|error| {
        format!("Local API request failed: {error}")
    })?;
    let status = response.status();
    let text = response.text().await.map_err(|error| error.to_string())?;
    let value = if status == reqwest::StatusCode::NO_CONTENT || text.trim().is_empty() {
        Value::Null
    } else {
        serde_json::from_str::<Value>(&text).unwrap_or_else(|_| {
            serde_json::json!({ "message": text })
        })
    };

    if !status.is_success() {
        let detail = value.get("detail")
            .or_else(|| value.get("message"))
            .and_then(Value::as_str)
            .unwrap_or("The local API rejected the request.");
        return Err(format!("HTTP {}: {}", status.as_u16(), detail));
    }
    Ok(value)
}

fn main() {
    let token = generate_local_token();
    let token_for_setup = token.clone();

    let app = tauri::Builder::default()
        .setup(move |app| {
            let child = start_local_api(app.handle(), &token_for_setup);
            let (child, startup_error) = match child {
                Ok(process) => (Some(process), None),
                Err(error) => (None, Some(error)),
            };

            let client = Client::builder()
                .connect_timeout(Duration::from_secs(2))
                .build()
                .map_err(|error| std::io::Error::other(error.to_string()))?;

            app.manage(PosApiState {
                client,
                token: token_for_setup.clone(),
                child: Mutex::new(child),
                startup_error,
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![api_status, pos_api_request])
        .build(tauri::generate_context!())
        .expect("failed to build Restaurant POS desktop application");

    app.run(|handle, event| {
        if let tauri::RunEvent::Exit = event {
            if let Some(state) = handle.try_state::<PosApiState>() {
                if let Ok(mut child) = state.child.lock() {
                    if let Some(process) = child.as_mut() {
                        let _ = process.kill();
                        let _ = process.wait();
                    }
                }
            }
        }
    });
}
