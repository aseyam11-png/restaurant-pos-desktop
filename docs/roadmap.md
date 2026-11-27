# Delivery Roadmap

Roadmap is a plan, not evidence of implementation.

## Phase 0 — Technical foundation
- Tauri + React desktop shell on Windows/macOS.
- Spring Boot local service packaged as sidecar.
- PostgreSQL start/stop/upgrade/backup spike.
- Real printer spike on both platforms.
- RTL/LTR design system.
**Exit:** written evidence for packaging, local service, printing, and database recovery on both OSs.

## Phase 1 — Product catalog and cashier
Categories, products, generated IDs, fixed/ask-at-sale pricing, search, cart, customer name, order type, immutable order-line snapshots.

## Phase 2 — Orders and printing
Order lifecycle, customer order paper, payment state machine, cancellation/audit, durable print queue/reprint, optional tables and riders.

## Phase 3 — Payments and operations
Cash/wallet/card/split tender, internal wallet sender phone excluded from print, cash drawer/shifts, purchases, suppliers, expenses, lightweight stock.

## Phase 4 — Reports and exports
Operational/financial reports, sales/payment/purchase analytics, profit/margins with validated costs, charts, PDF/XLSX, A4/thermal printing.

## Phase 5 — Reliability and multi-device
LAN host, terminal enrollment, offline recovery, concurrency, backup/restore/migrations, load/stress/soak/printer-failure testing.

## Phase 6 — Mobile manager and cloud
Cloud API, tenant isolation, outbox/idempotent sync, read-only mobile reports, optional cloud backup.

## Phase 7 — Commercial release
Subscription/perpetual licensing, signed licenses, offline grace, Windows/macOS signed/notarized installers, upgrade path, documentation and release evidence.
