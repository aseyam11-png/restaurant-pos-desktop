# Phase 0 Engineering Audit — 2026-11-28

## Executive result

**Verdict: FOUNDATION-ONLY / NOT A RUNNABLE PRODUCT**

The repository README explicitly states that production desktop UI, local API, PostgreSQL schema, printer integration, sync, licensing, and installers are not implemented. The product requirements, architecture outline, quality gates, and roadmap are planning artifacts, not implementation evidence.

Repository evidence inspected:
- `README.md`: current status and repository map.
- `docs/product/PRD.md`: v0.1 draft product requirements and acceptance criteria.
- `docs/architecture/overview.md`: candidate architecture and unresolved deployment decisions.
- `docs/quality/performance-and-quality-gates.md`: initial latency objectives and release blockers.
- `docs/roadmap.md`: delivery plan.
- Git history: four commits visible; latest inspected commit is `0847a4c7474e5a508981ca5479536ccd9e53d018` (2026-11-27).

The README describes a proposed repository map (`apps/desktop`, `services/local-api`, etc.). This audit has not established that all those directories contain implementation. Attempts to fetch root `package.json`, `index.html`, `src/main.js`, and `.github/workflows/ci.yml` returned 404. This is evidence those exact paths are absent on the default branch, not proof that no other tests or workflow files exist.

## Verified findings

### HIGH — No runnable application established
- Location: repository root / README current-status section.
- Trigger: attempting to build, install, or run the product based on this repository.
- Impact: no production desktop UI, local API, schema, printer integration, sync, licensing, or installers are declared implemented.
- Evidence: explicit current-status statement in README; exact common entry-point paths checked returned 404.
- Remediation: create and verify a minimal desktop shell and local service on a dedicated feature branch before claiming a runnable build.

### HIGH — Local database decision is unresolved
- Location: `README.md`, `docs/architecture/overview.md`, `docs/roadmap.md`.
- Trigger: selecting the persistence layer and packaging strategy.
- Impact: choosing PostgreSQL, SQLite, or another store without validating lifecycle, multi-terminal writes, backup/restore, migrations, and cross-platform packaging can create expensive rework.
- Evidence: README labels technology as candidate; architecture lists PostgreSQL and packaging questions as unresolved.
- Remediation: execute a bounded Windows/macOS deployment spike and record a decision with measured evidence.

### HIGH — No platform or printer verification evidence
- Location: `docs/architecture/overview.md`, `docs/quality/performance-and-quality-gates.md`.
- Trigger: claiming Windows/macOS support or thermal printer compatibility.
- Impact: native process lifecycle, permissions, signing, spooler behavior, and physical output may fail in real installations.
- Evidence: documents explicitly require executable platform/printer prototypes; no test evidence was present in inspected documents.
- Remediation: maintain a hardware/OS verification matrix and require physical printer tests before release.

### MEDIUM — Financial and operational rules remain draft requirements
- Location: `docs/product/PRD.md`.
- Trigger: implementing sale, payment, refund, inventory, or profit reporting.
- Impact: ambiguous rounding, tax, refund, stock-consumption, and payment-confirmation policies can create incorrect ledgers or reports.
- Evidence: PRD is marked Draft baseline v0.1; profit reporting itself is qualified by cost-data sufficiency.
- Remediation: convert each money-moving workflow into explicit invariants, examples, and automated tests before implementation.

## ECC 14-lens assessment

| Lens | Status | Reason |
|---|---|---|
| Requirements | COVERED | Draft PRD and acceptance criteria inspected; open policy details remain. |
| Correctness | SKIPPED | No executable business logic established to inspect. |
| Architecture | COVERED | Candidate component boundaries and unresolved decisions inspected. |
| Scope | COVERED | v1 scope and explicit exclusions documented. |
| Security | COVERED | Trust boundaries and secret-handling rules documented; implementation review remains blocked. |
| Data integrity / concurrency | COVERED | Required invariants are documented; no implementation evidence exists. |
| Resilience / idempotency | COVERED | Outbox, retry, recovery and deduplication requirements documented; not implemented. |
| Performance | COVERED | Initial measurable objectives and load profiles documented; no measurements. |
| APIs / dependencies | SKIPPED | No implemented service/API/dependency manifests were established. |
| Tests | SKIPPED | No runnable test suite or workflow was established from inspected paths. |
| Compatibility / migrations | COVERED | Cross-platform and migration risks identified in architecture/quality docs; no proof of execution. |
| Operations / deployment | COVERED | Deployment, backup, restore, signing and release requirements inspected; no installers. |
| Abuse | COVERED | Threat boundaries and sensitive-data exposure constraints identified; no executable threat tests. |
| Maintainability | COVERED | Proposed module boundaries and repository layout inspected; implementation quality cannot yet be assessed. |

## Architecture challenge before coding

The PRD targets both a single-computer installation and multiple terminals. These deployment modes have different persistence and concurrency needs. A local embedded database can simplify a single-machine install, while a LAN host serving several terminals needs robust concurrent writes, backup, and recovery. A packaged local PostgreSQL service may be viable but adds lifecycle, upgrade, permissions, and support complexity.

**No database choice is approved by this audit.** ADR-001 defines the evidence required before selection.

## Phase 0 exit criteria

1. Minimal desktop shell launches on Windows and macOS.
2. Local service starts, reports health, shuts down, and restarts cleanly.
3. Candidate database survives restart and passes backup/restore drill.
4. Two clients can execute concurrent test writes without loss or duplication.
5. A physical 80mm or 58mm printer test separates queue acceptance from actual paper output.
6. RTL/LTR shell is usable at supported desktop scaling.
7. CI runs reproducible build and test commands.
8. Evidence and limitations are recorded; no capability is claimed without proof.

## Next implementation slice

Build only the smallest vertical foundation: desktop shell + local service lifecycle + health check + persistence spike + automated smoke tests. Do not implement payments, accounting, inventory, or cloud sync until the foundation decisions are validated.
