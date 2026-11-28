# Restaurant POS Desktop

Cross-platform, offline-first desktop POS for restaurants, cafés, cafeterias, and small food businesses.

**Repository:** Public · **Phase:** Product and technical foundation · **Targets:** Windows and macOS

> This repository is under active development. A feature is not implemented until code, tests, and platform verification exist.

## Product scope
- Categories and products with system-generated product numbers.
- Product pricing modes: fixed price or cashier-entered price at sale time.
- Desktop cashier with category navigation, search, mouse, keyboard, and optional touch.
- Dine-in, takeaway, and delivery orders; optional table management.
- Customer name, order number, date/time, order type, quantities, prices, and total on the customer order document.
- Thermal printing, printer queue/retry, and keyboard shortcuts.
- Cash, wallet, card, and split payments.
- Internal-only wallet transfer sender phone number; never printed on customer or kitchen documents.
- Purchases, suppliers, expenses, cash drawer, shifts, and lightweight inventory.
- Operational/financial reports, charts, PDF and XLSX export.
- Backup, restore, and export to local/removable storage.
- Local owner account plus read-only mobile manager dashboard.
- Subscription and perpetual licensing; white-label identity per customer.
- Local-first operation without internet; cloud sync when available.

## Technology direction
| Layer | Candidate |
|---|---|
| Desktop shell | Tauri 2 |
| Desktop UI | React + TypeScript |
| Native integration | Rust |
| Local business API | Java + Spring Boot |
| Local database | PostgreSQL |
| Migrations | Flyway |
| Mobile manager | Next.js + TypeScript |
| Charts | Apache ECharts |
| Excel | Apache POI |
| Tests | JUnit, Testcontainers, Playwright, platform/printer smoke tests |

These are candidate decisions. Validate service lifecycle, database packaging, real printers, signing, upgrades, and recovery on Windows and macOS before finalizing.

## Repository map
- `apps/desktop` — desktop app.
- `apps/manager-web` — manager dashboard.
- `services/local-api` — local Spring Boot API.
- `services/cloud-api` — licensing, sync, remote reports.
- `packages/contracts` — API and event contracts.
- `docs` — product, architecture, security, quality, roadmap.
- `prototype` — UX experiments only.
- `infra` — packaging and operations.

## Local database prerequisite

The current desktop build includes a first-run PostgreSQL connection screen. It stores the database password in Windows Credential Manager or macOS Keychain and stores only non-secret connection metadata in the application config directory.

PostgreSQL itself is a separate prerequisite in this development release. Install and start a local PostgreSQL server, create the non-superuser application role/database with the scripts under `scripts/database`, then enter the connection details in the application. The application currently supports loopback database connections only.

See [local PostgreSQL setup](docs/operations/local-database-setup.md). The installer does not yet install or bundle PostgreSQL automatically.

## Non-negotiable rules
1. Local DB is the source of truth for in-store sales.
2. Internet is never required to create a local order.
3. Order persistence, print queue acceptance, and physical output are separate states.
4. Accepted orders are never hard-deleted; cancellation is auditable.
5. Preparation and payment have independent state machines.
6. Transfer sender phone number is internal report data only; never print it.
7. Historical order lines preserve product, unit, and price snapshots.
8. Printing, reporting, backup, and sync must not block cashier UI.
9. Do not claim platform/printer/performance capability before testing it.
10. Never commit credentials, customer data, wallet secrets, signing keys, or production backups.

## Current status

The `phase-0/engineering-baseline` branch now contains a React/TypeScript + Tauri desktop shell, a Spring Boot local API, PostgreSQL/Flyway migrations, initial cashier/catalog/order workflows, inventory and dining operations, purchasing, expenses, cash shifts, delivery riders, local staff authentication, role checks, store settings, server-side tax calculation, and a first-run database connection screen.

These capabilities are under active development and are not yet a production release. The desktop shell currently requires a separately installed local PostgreSQL server; the installer does not bundle PostgreSQL. The printer workflow still relies on the operating-system print dialog and has not passed physical printer verification. Multi-terminal LAN operation, cloud synchronization, mobile manager dashboard, a hardened backup/restore drill, a separate restricted database runtime role, licensing, signed/notarized installers, and integrated Windows/macOS runtime tests remain release gates. CI results must be inspected on the current branch head before any build is described as passing.

See [PRD](docs/product/PRD.md), [architecture](docs/architecture/overview.md), [quality gates](docs/quality/performance-and-quality-gates.md), and [roadmap](docs/roadmap.md).
