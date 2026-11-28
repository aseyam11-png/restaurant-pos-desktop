# Local PostgreSQL setup

**Status:** Supported setup prerequisite for the current development build. The Windows/macOS application bundle does not yet contain a PostgreSQL server installer or server binaries.

## 1. Install and start PostgreSQL

Install a supported PostgreSQL server on the same computer. Ensure the service is running and listening on `127.0.0.1:5432` (or the chosen local port). The application intentionally accepts only loopback addresses in this release; it must not send database credentials to a remote host over an unencrypted connection.

Install the PostgreSQL command-line tools (`psql`) and ensure they are available in PATH.

## 2. Create the application database and role

Run the platform helper from the repository:

**Windows PowerShell**
```powershell
.scriptsdatabaseootstrap-postgres.ps1
```

**macOS**
```bash
chmod +x ./scripts/database/bootstrap-postgres.sh
./scripts/database/bootstrap-postgres.sh
```

The script prompts interactively for the PostgreSQL administrator credential and the new application password. It creates (or updates) a dedicated non-superuser role and a database owned by that role. It does not create a superuser application account.

Do not paste credentials into command-line arguments, source files, issue comments, or GitHub Actions logs.

## 3. Configure Restaurant POS

Launch the desktop application and enter:
- Host: `127.0.0.1`
- Port: `5432` (or the port used by the local service)
- Database: `restaurant_pos`
- Username: `restaurant_pos`
- Password: the application role password created in step 2

The desktop shell stores the password in Windows Credential Manager or macOS Keychain. The adjacent JSON settings file contains only host, port, database, and username.

The Spring Boot local API then starts and runs Flyway migrations. The first run can take longer while schema migrations are applied. The login and first-owner setup appear only after the authenticated local API health check succeeds.

## 4. Troubleshooting

- **Connection refused:** verify that the PostgreSQL service is running and listening on the configured loopback address and port.
- **Authentication failed:** rerun the helper with the correct administrator credentials and set a new application role password, then save the same password in the application setup screen.
- **Database does not exist:** rerun the helper or verify the database name.
- **Migration failure:** preserve the database and local API log before attempting repair. Do not delete `flyway_schema_history` or drop tables as a troubleshooting shortcut.
- **Local API logs:** use the app log directory for the current OS account. The file is named `local-api.log`.

## 5. Current limitations

- PostgreSQL is a separate prerequisite; it is not installed automatically by the POS installer yet.
- The current desktop setup only connects to a PostgreSQL server on the same device. Multi-terminal LAN hosting, TLS configuration, database role separation for migrator/runtime, automated database backup/restore, and signed/notarized production installers remain release gates.
- The PostgreSQL password is stored in the OS credential vault, but the application user currently owns its database so Flyway can apply schema changes. A separate restricted runtime role is a required hardening step before commercial release.
- A successful database connection is not proof of backup/restore readiness or production reliability. The release process still requires the complete integrated test suite and physical printer verification.
