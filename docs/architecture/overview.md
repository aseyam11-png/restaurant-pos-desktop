# Architecture Overview

## 1. Principle
Use a local-first modular desktop architecture. The local database is the source of truth for in-store operations. Cloud services support licensing, synchronization, remote manager reporting, and optional off-device backup. Local sale execution must not require cloud availability.

## 2. Components
### Desktop shell
- Tauri 2 is the preferred prototype candidate, not final.
- React + TypeScript UI.
- Rust/Tauri commands and plugins for native integration.
- UI never connects directly to PostgreSQL.

### Local API
- Java + Spring Boot owns validation, business rules, authorization, transactions, audit events.
- Local REST API; WebSocket only where live updates are needed.
- Separate durable printer worker/queue.

### Local database
- PostgreSQL candidate; Flyway migrations.
- Explicit transactions for order/payment/cancel/stock.
- Backup, restore, and upgrade-path tests.

### Cloud services
- Cloud API for licensing, sync, remote reporting.
- Transactional outbox on local DB.
- Local transactions remain independent from cloud.

### Mobile manager
- Next.js + TypeScript.
- Read-only reports by default.
- Shows last successful sync timestamp.

## 3. Deployment modes
**Single computer:** desktop shell, local API, and DB on one machine. UI calls a loopback-only API with authenticated local session and suitable origin/CSRF protections.

**Multiple terminals:** in-store host runs local API and DB; terminals connect over authenticated, encrypted LAN. Define behavior for LAN interruption and host recovery.

## 4. Trust boundaries
- Never trust tenant, role, price, payment status, or permissions provided only by UI.
- Local API enforces business rules.
- Sender phone number is excluded from customer receipt/preparation DTOs by construction.
- Cloud sync validates tenant ownership and idempotency.
- No signing private key or production secret in repository/installer.
- Backups protect confidentiality and integrity.

## 5. Order and printing
1. Validate order lines and pricing mode.
2. In one local transaction persist order, immutable line snapshots, audit event, outbox event.
3. Return committed order reference to UI.
4. Create durable print job with stable ID.
5. Track queued, dispatched, confirmed/printed where observable, failed separately.
6. Payment remains a separate aggregate/state machine.

A successful enqueue/spooler handoff is not proof that paper physically exited the printer.

## 6. Sync
Transactional outbox, stable UUID per event, stable idempotency keys, retry/backoff, server-side deduplication, and explicit reconciliation. No blind last-write-wins for money.

## 7. Performance
Keep UI work small; use indexed and paginated queries; move printing, reports, exports, backups, and sync to background workers; use bounded queues/backpressure; preserve ACID transactions.

## 8. Prototype decisions
1. Bundle/manage PostgreSQL locally or install as managed prerequisite?
2. Java runtime packaging and lifecycle on both OSs?
3. Native printing vs ESC/POS per printer model?
4. Tauri sidecar packaging, signing, updates?
5. LAN security and multi-device recovery?
6. Safe schema migrations and backups?
All require executable Windows/macOS prototypes.
