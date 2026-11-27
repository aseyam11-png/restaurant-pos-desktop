# Contributing

## Before coding
1. Read PRD and architecture.
2. Challenge requirements, assumptions, failure cases, security, concurrency, data integrity, migration, performance, and scope.
3. Keep changes focused.

## Engineering expectations
- TypeScript strict mode.
- Java input validation, explicit transaction boundaries, and error handling.
- DB changes require Flyway migrations and upgrade tests.
- Business rules must not live exclusively in desktop UI.
- UI-provided price, role, tenant, or payment status must be validated by API.
- Order, payment, cancellation, and print states are modeled independently.
- Long-running tasks stay off the UI thread.
- Verify Arabic RTL and English LTR.
- Justify new dependencies and maintenance cost.

## Pull requests include
Problem, behavior, scope, risks, data/migration impact, tests and results, OS/printer hardware tested, UI screenshots, and known limitations.

Compilation alone does not prove completion. Verify acceptance criteria with evidence.
