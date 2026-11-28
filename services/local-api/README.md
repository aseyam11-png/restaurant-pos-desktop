# Restaurant POS Local API

Spring Boot service that owns catalog validation, order creation, payment confirmation, audit history, and the durable customer-print queue. The desktop UI must never connect directly to PostgreSQL.

## Implemented in this slice

- Health endpoint at `GET /actuator/health`.
- Local-token protected catalog category and product endpoints.
- Fixed-price and cashier-entered pricing modes.
- Server-side order totals using `BigDecimal` only.
- Order idempotency using a unique key and a SHA-256 request fingerprint.
- Immutable order-line snapshots.
- Separate preparation and payment state machines.
- Wallet/card payment confirmation workflow; wallet sender phone is stored only on the internal payment row.
- Cancellation is audited and does not hard-delete an order.
- Transactional audit event, outbox event, and customer-order print-job creation.
- Reprint jobs refer to the original order and do not create another sale.
- Sales summary and product sales reports.
- PostgreSQL schema managed by Flyway.

## Important limitations

This is an initial real API slice, not yet the complete restaurant accounting system. No physical printer worker, stock ledger, purchasing, expenses, cash shifts, refund workflow, user/role management, cloud sync, LAN device enrollment, or installer-managed database lifecycle is implemented. A queued print job is not proof that paper was physically printed. Profit is not reported until costing data exists.

The service binds to loopback by default and requires a separately configured local token for every non-health endpoint. The desktop secure credential handoff is not yet wired; do not put the token in source control or browser local storage. Multi-terminal/LAN access must not be enabled until per-device authentication and encrypted transport are implemented.

## Local development

Requires Java 21, Maven 3.9+, and Docker Compose.

1. Set environment variables from `.env.example` in your local process environment. Generate strong values for `POS_DB_PASSWORD` and `POS_LOCAL_TOKEN`; do not reuse example values.
2. Start PostgreSQL:

   ```sh
   docker compose up -d
   ```

3. Start the API:

   ```sh
   mvn spring-boot:run
   ```

4. Verify health:

   ```sh
   curl http://127.0.0.1:17890/actuator/health
   ```

All `/api/v1/**` calls require header `X-POS-Local-Token`. Order creation additionally requires a unique `Idempotency-Key`.

## API outline

- `GET/POST /api/v1/catalog/categories`
- `GET/POST /api/v1/catalog/products`
- `PATCH /api/v1/catalog/products/{id}/deactivate`
- `GET/POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/cancel`
- `PATCH /api/v1/orders/{id}/preparation`
- `POST /api/v1/orders/{id}/payments/{paymentId}/confirm`
- `POST /api/v1/orders/{id}/reprints`
- `GET /api/v1/print-jobs`
- `GET /api/v1/reports/sales/summary`
- `GET /api/v1/reports/sales/products`

Default reporting timezone is UTC until the store timezone setting is implemented. Configure the store currency before use; default is ILS to match the existing prototype, not a market lock-in.
