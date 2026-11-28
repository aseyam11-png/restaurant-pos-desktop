#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::{
    process::{Child, Command, Stdio},
    sync::Mutex,
    time::Duration,
};

use rand::{rngs::OsRng, RngCore};
use reqwest::{Client, Method};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tauri::{AppHandle, Manager, State};

const API_BASE_URL: &str = "http://127.0.0.1:17890";

#[derive(Clone)]
struct RuntimeDatabaseConfig {
    host: String,
    port: u16,
    database: String,
    username: String,
    password: String,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DatabaseConfigFile {
    host: String,
    port: u16,
    database: String,
    username: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DatabaseConfigStatus {
    configured: bool,
    host: Option<String>,
    port: Option<u16>,
    database: Option<String>,
    username: Option<String>,
}

struct PosApiState {
    client: Client,
    token: String,
    child: Mutex<Option<Child>>,
    startup_error: Mutex<Option<String>>,
    database_config: Mutex<Option<RuntimeDatabaseConfig>>,
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

fn start_local_api(
    app: &AppHandle,
    token: &str,
    database: &RuntimeDatabaseConfig,
) -> Result<Child, String> {
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
        .env("POS_SECURITY_LOCAL_TOKEN", token)
        .env(
            "POS_DB_URL",
            format!("jdbc:postgresql://{}:{}/{}", database.host, database.port, database.database),
        )
        .env("POS_DB_USERNAME", &database.username)
        .env("POS_DB_PASSWORD", &database.password)
        .env("POS_CURRENCY", std::env::var("POS_CURRENCY").unwrap_or_else(|_| "ILS".to_string()))
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|error| format!("Could not start the local API process: {error}"))
}

fn validate_database_config(
    host: &str,
    port: u16,
    database: &str,
    username: &str,
    password: &str,
) -> Result<RuntimeDatabaseConfig, String> {
    if !matches!(host, "localhost" | "127.0.0.1") {
        return Err("For this single-computer release, PostgreSQL must listen on localhost.".to_string());
    }
    if port == 0 {
        return Err("PostgreSQL port must be between 1 and 65535.".to_string());
    }
    let valid_identifier = |value: &str| {
        !value.is_empty()
            && value.len() <= 63
            && value.as_bytes()[0].is_ascii_alphabetic()
            && value.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
    };
    if !valid_identifier(database) || !valid_identifier(username) {
        return Err("Database and username must be valid PostgreSQL identifiers.".to_string());
    }
    if password.is_empty() || password.len() > 256 || password.contains('\0') {
        return Err("Database password must contain 1 to 256 characters.".to_string());
    }
    Ok(RuntimeDatabaseConfig {
        host: host.to_string(),
        port,
        database: database.to_string(),
        username: username.to_string(),
        password: password.to_string(),
    })
}

fn database_password_entry() -> Result<keyring::Entry, String> {
    keyring::Entry::new("com.restaurantpos.desktop", "postgresql-password")
        .map_err(|error| format!("Could not access the operating-system credential vault: {error}"))
}

fn persist_database_config(
    app: &AppHandle,
    config: &RuntimeDatabaseConfig,
) -> Result<(), String> {
    let directory = app.path().app_config_dir().map_err(|error| error.to_string())?;
    std::fs::create_dir_all(&directory).map_err(|error| error.to_string())?;
    let file_path = directory.join("database.json");
    let temporary_path = directory.join("database.json.tmp");
    database_password_entry()?
        .set_password(&config.password)
        .map_err(|error| format!("Could not store the database password securely: {error}"))?;
    let file_config = DatabaseConfigFile {
        host: config.host.clone(),
        port: config.port,
        database: config.database.clone(),
        username: config.username.clone(),
    };
    let bytes = serde_json::to_vec_pretty(&file_config).map_err(|error| error.to_string())?;
    std::fs::write(&temporary_path, bytes).map_err(|error| error.to_string())?;
    std::fs::rename(&temporary_path, &file_path).map_err(|error| error.to_string())?;
    Ok(())
}

fn load_database_config(app: &AppHandle) -> Result<Option<RuntimeDatabaseConfig>, String> {
    let directory = app.path().app_config_dir().map_err(|error| error.to_string())?;
    let file_path = directory.join("database.json");
    if !file_path.exists() {
        return Ok(None);
    }
    let bytes = std::fs::read(&file_path).map_err(|error| error.to_string())?;
    let file_config: DatabaseConfigFile = serde_json::from_slice(&bytes)
        .map_err(|error| format!("The saved database configuration is invalid: {error}"))?;
    let password = database_password_entry()?
        .get_password()
        .map_err(|error| format!("The saved database password is unavailable in the OS credential vault: {error}"))?;
    validate_database_config(
        &file_config.host,
        file_config.port,
        &file_config.database,
        &file_config.username,
        &password,
    ).map(Some)
}

#[tauri::command]
fn database_config_status(state: State<'_, PosApiState>) -> DatabaseConfigStatus {
    match state.database_config.lock() {
        Ok(config) => match config.as_ref() {
            Some(config) => DatabaseConfigStatus {
                configured: true,
                host: Some(config.host.clone()),
                port: Some(config.port),
                database: Some(config.database.clone()),
                username: Some(config.username.clone()),
            },
            None => DatabaseConfigStatus {
                configured: false,
                host: None,
                port: None,
                database: None,
                username: None,
            },
        },
        Err(_) => DatabaseConfigStatus {
            configured: false,
            host: None,
            port: None,
            database: None,
            username: None,
        },
    }
}

#[tauri::command(rename_all = "camelCase")]
fn configure_database(
    app: AppHandle,
    state: State<'_, PosApiState>,
    host: String,
    port: u16,
    database: String,
    username: String,
    password: String,
) -> Result<DatabaseConfigStatus, String> {
    let config = validate_database_config(&host, port, &database, &username, &password)?;
    persist_database_config(&app, &config)?;

    {
        let mut child = state.child.lock().map_err(|_| "Local API process lock is unavailable.".to_string())?;
        if let Some(mut running) = child.take() {
            let _ = running.kill();
            let _ = running.wait();
        }
        match start_local_api(&app, &state.token, &config) {
            Ok(process) => *child = Some(process),
            Err(error) => {
                if let Ok(mut startup_error) = state.startup_error.lock() {
                    *startup_error = Some(error.clone());
                }
                return Err(error);
            }
        }
    }
    *state.database_config.lock().map_err(|_| "Database configuration lock is unavailable.".to_string())? = Some(config.clone());
    *state.startup_error.lock().map_err(|_| "Startup state lock is unavailable.".to_string())? = None;

    Ok(DatabaseConfigStatus {
        configured: true,
        host: Some(config.host),
        port: Some(config.port),
        database: Some(config.database),
        username: Some(config.username),
    })
}

#[tauri::command]
async fn api_status(state: State<'_, PosApiState>) -> ApiStatus {
    if let Ok(error) = state.startup_error.lock() {
        if let Some(message) = error.as_ref() {
            return ApiStatus {
                available: false,
                message: message.clone(),
            };
        }
    }

    let health = state.client
        .get(format!("{API_BASE_URL}/actuator/health"))
        .timeout(Duration::from_secs(2))
        .send()
        .await;

    match health {
        Ok(response) if response.status().is_success() => {
            let authenticated = state.client
                .get(format!("{API_BASE_URL}/api/v1/auth/setup-status"))
                .header("X-POS-Local-Token", &state.token)
                .timeout(Duration::from_secs(2))
                .send()
                .await;

            match authenticated {
                Ok(response) if response.status().is_success() => ApiStatus {
                    available: true,
                    message: "Local API and database are ready".to_string(),
                },
                Ok(response) => ApiStatus {
                    available: false,
                    message: format!("Local API authentication check returned HTTP {}", response.status()),
                },
                Err(_) => ApiStatus {
                    available: false,
                    message: "Local API authentication check failed".to_string(),
                },
            }
        }
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
    session_token: Option<String>,
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

    if let Some(token) = session_token {
        if token.trim().is_empty() || token.len() > 256 {
            return Err("The local user session token is invalid.".to_string());
        }
        request = request.header("Authorization", format!("Bearer {}", token));
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
        .plugin(tauri_plugin_single_instance::init(|_app, _argv, _cwd| {}))
        .setup(move |app| {
            let (loaded_config, configuration_error) = match load_database_config(app.handle()) {
                Ok(config) => (config, None),
                Err(error) => (None, Some(error)),
            };
            let (database_config, child, startup_error) = match loaded_config {
                Some(config) => {
                    match start_local_api(app.handle(), &token_for_setup, &config) {
                        Ok(process) => (Some(config), Some(process), None),
                        Err(error) => (Some(config), None, Some(error)),
                    }
                }
                None => (
                    None,
                    None,
                    Some(configuration_error.unwrap_or_else(|| {
                        "Configure a local PostgreSQL database to finish first-time setup.".to_string()
                    })),
                ),
            };

            let client = Client::builder()
                .connect_timeout(Duration::from_secs(2))
                .build()
                .map_err(|error| std::io::Error::other(error.to_string()))?;

            app.manage(PosApiState {
                client,
                token: token_for_setup.clone(),
                child: Mutex::new(child),
                startup_error: Mutex::new(startup_error),
                database_config: Mutex::new(database_config),
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            api_status,
            pos_api_request,
            database_config_status,
            configure_database,
        ])
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
