# ADR-001: Local persistence and deployment topology

- **Status:** Proposed — decision deferred pending executable spike
- **Date:** 2026-11-28
- **Owners:** Product/engineering
- **Scope:** Local-first Restaurant POS v1

## Context

The product must support:
1. One computer running the desktop application and local services.
2. Multiple cashier/preparation terminals connecting to an in-store host over LAN.
3. Sales and order capture without internet.
4. Durable financial transactions, auditable cancellations/refunds, backup/restore, and safe upgrades.
5. Windows and macOS packaging and support.

The current architecture names PostgreSQL as a candidate local database. That is not yet an evidence-backed decision. Selecting an embedded store solely for simple packaging risks multi-terminal write contention and recovery complexity. Selecting PostgreSQL without validating service installation, process lifecycle, permissions, upgrade, backup, and restore risks a fragile desktop installer.

## Options to test

### A. PostgreSQL managed local service
- Strengths: mature concurrent writes, transactions, indexing, operational tooling, and a clear host/server topology.
- Risks: service lifecycle and installation, port/security configuration, upgrades, disk permissions, backup consistency, and uninstall/repair behavior.

### B. SQLite in WAL mode behind one local API
- Strengths: simple single-file deployment, low operational overhead, transactional persistence, and easy local backup patterns when implemented correctly.
- Risks: write serialization, busy handling, LAN workload limits, migration/backup coordination, and ensuring no client accesses the database file directly over a network share.

### C. Hybrid deployment
- Single-computer mode uses embedded persistence; multi-terminal host uses PostgreSQL.
- Strengths: can optimize simple and multi-terminal deployments separately.
- Risks: two database dialects, migration parity, behavior divergence, more test combinations, and support burden.

## Security and integrity constraints

- Desktop UI never connects directly to a database.
- All writes pass through the local business API.
- The database must not be exposed directly to LAN clients.
- Order, payment, audit, and outbox records commit atomically where required.
- Stable idempotency keys prevent retries from creating duplicate sales or payments.
- Financial state must never use blind last-write-wins.
- Backup must be consistent during active traffic; restore must validate integrity and preserve a pre-restore backup.
- Database files, backups, logs, and credentials must have restrictive permissions.
- Migrations must be tested against upgrade and rollback/recovery procedures.

## Required spike and acceptance evidence

Run the same workload against each viable option on Windows and macOS:

1. Install, launch, restart, upgrade, repair, and uninstall behavior.
2. Service start/stop/crash recovery and health reporting.
3. 2–5 concurrent clients creating orders and stock movements through the API.
4. Retry/replay tests proving no duplicate order/payment.
5. Sustained and burst writes; capture P50/P95 latency, busy/lock errors, CPU, RAM, and disk growth.
6. Backup during writes, restore to a clean installation, and reconcile record counts/totals.
7. Schema migration from previous version and recovery from interrupted migration.
8. LAN interruption and host restart behavior.
9. Least-privilege file/service/network permissions.
10. Test evidence and exact OS/runtime versions.

## Decision rule

Choose the simplest option that passes every integrity, recovery, concurrency, packaging, and support gate on both target operating systems. Performance objectives are measured, not assumed. If SQLite cannot meet the multi-terminal workload safely, do not force it into that mode. If PostgreSQL packaging is too operationally fragile, document the additional installation prerequisite or test a managed hybrid.

## Consequences

Until the spike passes:
- No database technology is treated as final.
- Business logic must depend on repository/service interfaces, not database-specific SQL in UI code.
- No production data migration is introduced.
- No claim of offline-ready or multi-terminal-ready behavior is allowed.
