# Performance and Quality Gates

Performance, reliability, and visual consistency are release gates.

## Initial latency objectives
Targets are measurable goals, not present capabilities or guarantees. Measure P50/P95 on named reference hardware.

| Operation | Initial objective |
|---|---:|
| Add item to order UI | P95 < 100ms |
| Recalculate displayed total | P95 < 100ms |
| Local catalog search | P95 < 200ms |
| Durable local order commit | P95 < 500ms |
| Enqueue print job after commit | P95 < 300ms |

Keep separate measurements for UI response, durable DB commit, print enqueue, OS spooler handoff, and physical output.

## Load profiles
Define catalog size, average order lines, transactions/hour, peak burst, terminal count, report range, and reference hardware. Test expected load, sustained peak, burst above peak, long-running soak, DB growth, and report-heavy workloads.

## Required tests
- Unit/integration tests for pricing modes, totals, cancel, payment, refund, audit history.
- Concurrency tests for simultaneous orders, payment updates, stock moves.
- Retry/idempotency tests for repeated clicks and sync replay.
- Printer disconnect, out-of-paper, spooler failure, reprint.
- Offline and reconnect/sync.
- Backup during live traffic and restore to clean environment.
- Multi-terminal LAN interruption/recovery.
- Windows/macOS installer, update, permissions, printer.
- UI responsiveness, accessibility, RTL/LTR consistency.

## UI/UX gates
- Consistent typography, spacing, component behavior, icons, colors, status meanings.
- Arabic RTL and English LTR verification.
- Desktop monitor sizes, resizing, display scaling.
- Mouse/keyboard primary workflow; optional touch.
- Clear validation, progress, offline, and printer failure state.
- No unnecessary blocking modal or spinner during routine cashier flow.

## Release blockers
Lost/duplicated order/payment; wrong totals; historical price mutation; sender phone printed; UI freeze; printer failure loses order; failed restore; cross-customer data access; cloud outage stops local sales; migration or update corrupts DB.

## Evidence
Each release candidate includes commands/results, hardware profile, P50/P95, load/soak/concurrency summary, tested printers/OSs, backup/restore proof, Arabic/English UI screenshots, and known limitations.
