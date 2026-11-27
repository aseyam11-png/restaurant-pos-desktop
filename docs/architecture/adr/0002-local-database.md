# ADR-0002: Local Database Candidate

- **Status:** Proposed; packaging and recovery prototype required.
- **Date:** 2026-11-27.

## Context
The system must work offline on one computer and support multiple terminals through an in-store host, with transactional writes and reliable recovery.

## Decision
Evaluate PostgreSQL as a shared local database for single-device and multi-device modes. Spring Boot owns all DB access; Flyway manages migrations.

## Consequences
- One engine simplifies schema, business logic, and tests.
- Bundling/managing PostgreSQL on Windows/macOS adds installer, service, permission, backup, restore, and upgrade complexity.
- Database health and migrations must pass before sales start.
- Data directory must survive app upgrades and uninstall unless owner explicitly chooses deletion.
- Restore drill is mandatory.

## Alternatives
SQLite (simpler installation/single-device fit; multi-terminal access must go through host service); separate engines by deployment mode (more compatibility burden).
