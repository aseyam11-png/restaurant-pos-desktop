# ADR-001: Local persistence and deployment topology

- **Status:** PostgreSQL selected for the initial local-service implementation; Windows/macOS service packaging remains unvalidated.
- **Date:** 2026-11-28
- **Scope:** Local-first Restaurant POS v1

## Context

The product must support one computer and multiple cashier/preparation terminals connecting to an in-store host over LAN. Sales and order capture must not require internet. Financial transactions, cancellations/refunds, audit, backup/restore, and safe upgrades must be durable.

## Decision

Use PostgreSQL as the single local-service database engine for the initial implementation. Spring Boot is the only process permitted to access the database. The desktop UI and terminals call the business API; they never connect directly to PostgreSQL or share database files over a network.

This is an implementation decision, not a claim that the database can already be installed, upgraded, or recovered automatically on Windows/macOS.

## Rationale

- The product has a stated multi-terminal host mode and sustained/burst workload requirements.
- A single PostgreSQL engine avoids dual-dialect migrations and differences in monetary transaction behavior between single-device and host modes.
- A single local API centralizes idempotency, business validation, authorization, audit, and concurrency handling.

## Required packaging and operational spike

Before a production installer is approved, prove on Windows and macOS:
1. Service start/stop, restart after crash, health check, and graceful shutdown.
2. Data-directory permissions and preservation across upgrades/uninstall.
3. Database installation/bootstrap, role creation, migrations, patch updates, and rollback/recovery procedures.
4. Consistent backup during active transactions and restore to a clean installation.
5. 2–5 concurrent clients creating orders/payment/stock operations via the API without loss or duplicate posting.
6. Network isolation: database listens only on loopback; LAN clients use authenticated encrypted API transport.
7. CPU, RAM, disk growth, P50/P95 write latency, and lock behavior under expected and burst workloads.
8. Real installer repair and data-retention tests.

## Security and integrity constraints

- Database is never exposed directly to LAN clients.
- Local API requires a high-entropy token for loopback access; this token must be delivered through OS-protected storage in the desktop app.
- Multi-terminal access requires individual device credentials and TLS; the current shared local token is not approved for LAN.
- Order, line snapshots, payment, audit, print queue, and outbox writes are transactional.
- Stable idempotency keys prevent retries from creating duplicate sales.
- Financial state never uses blind last-write-wins.
- Backups must be encrypted where appropriate and validated by restore drills.
- Database secrets and signing keys are never committed or shipped as plaintext.

## Consequences

- PostgreSQL is the implementation target for the local API.
- Desktop sidecar lifecycle and signed database packaging remain release blockers.
- No LAN mode, production rollout, or support claim is allowed until platform, recovery, concurrency, and security evidence passes.
