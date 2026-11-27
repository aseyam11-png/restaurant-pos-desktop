# Product Requirements — Restaurant POS Desktop

**Version:** 0.1 · **Status:** Draft baseline · **Platforms:** Windows and macOS · **Model:** Commercial desktop, local-first

## 1. Purpose and customers
Build a reliable POS for restaurants, cafés, cafeterias, fast-food shops, and small food businesses. Prioritize cashier speed, reliable local transactions, customer order paper, thermal printing, and operational/financial reporting.

Sell monthly subscriptions, annual subscriptions, and perpetual licenses. License terms must specify update/support entitlement and device limits. Each installation can use its own application name, business name, logo, language, currency, and receipt identity.

## 2. Users and deployment
- **Local owner account:** one primary owner/admin account for catalog, cashier, reports, purchases, expenses, printers, backup, and settings.
- **Mobile manager account:** separate read-only reporting account by default; shows sales, orders, cancellations, purchases, expenses, shifts, and profitability; displays last sync time.
- **Single computer:** app, local API, and database run on one machine.
- **Multiple terminals:** an in-store host runs API/database; cashier and prep terminals connect over LAN.
- Cloud is for licensing, sync, remote reports, and optional off-device backup. Local sales must not depend on cloud.

## 3. White-label identity
Per customer settings: application name, business name, app/business logos, accent/theme, currency, locale, address, contact information, and receipt header/footer. A customer-specific installer name/icon may be offered separately; runtime branding must not require a separate codebase.

## 4. Product catalog
Categories can be created, renamed, sorted, disabled, or restored. Product fields:
- System-generated unique internal product number.
- Name, category, unit, optional image, prep station, active state.
- Pricing mode: `FIXED` (owner sets default price; cashier adds at that price) or `ASK_AT_SALE` (cashier must enter unit price at sale).
- Search by product name, partial name, or internal number.
- Disable products used by historical transactions instead of hard deletion.
- Store immutable product name, number, unit, and actual sale-price snapshots on order lines.
- Internal product number is not printed on the customer order paper.

## 5. Cashier flow
1. Start an order and add products through categories or search.
2. Enter quantity; for `ASK_AT_SALE`, enter unit price.
3. Enter customer name.
4. Select order type: dine-in, takeaway, delivery.
5. For dine-in, select table if enabled; for delivery, enter delivery details and optionally assign rider.
6. Select payment method and amounts.
7. For wallet payments only, optionally enter the sender phone number as internal reporting data.
8. Confirm order. Local transaction must commit before print job is queued.
9. Print the primary customer order document, which the customer presents to the worker for preparation.

## 6. Customer order document
The agreed primary paper is one compact, readable document the customer takes to the worker. Do not assume two mandatory slips in v1. It contains business name/logo, order/invoice references, customer name, date/time, clearly visible order type, product names, quantities, prices, line totals, discounts/tax if applicable, and grand total.

It must not contain internal product numbers, wallet transfer sender number, passwords, or technical metadata. A separate kitchen-station ticket may be configurable later, but is not the primary v1 document.

## 7. Order and payment states
Preparation states: `NEW`, `IN_PREPARATION`, `READY`, `DELIVERED`, `CANCELLED`.

Payment states are independent: `UNPAID`, `PENDING_CONFIRMATION`, `PARTIALLY_PAID`, `PAID`, `REFUNDED`.

Accepted orders are never hard-deleted. Cancellation records actor, timestamp, reason, whether preparation started, and any resulting waste. Reprint is linked to the same order and cannot create another sale. Refunds are separate auditable events linked to original payment.

Payment methods: cash, wallet transfer, bank card, split tender. Recording sender phone does not prove funds arrived; confirmation state must be explicit. Never store wallet PINs, passwords, or OTPs.

## 8. Tables and delivery
- Optional floor/table map, zones, table status, linked orders, and audited transfers.
- Delivery rider directory: name, phone, availability, notes; assign rider and provide a call action.
- GPS tracking is out of scope for v1.

## 9. Purchases, expenses, cash, shifts, stock
- Suppliers and purchase invoices with date/number, line items, quantities, unit costs, discounts, paid/outstanding amounts, and optional attachments.
- Expenses with category, amount, date, notes, optional receipt.
- Cash drawer: opening cash, cash in/out, payment-method totals, expected/count closing cash, variance, audit.
- Lightweight stock: receipts, consumption, spoilage, adjustments, stock count, minimum levels.
- Recipe/BOM can be added later to calculate ingredient usage and COGS.

Purchases are not equal to cost of goods sold. Profit reporting requires reliable inventory/costing and must disclose incomplete cost data.

## 10. Reports and analytics
Periods: today, yesterday, this week, last week, this month, last month, custom range.

Operational reports: all orders, delivered, cancelled/reasons, active/prep, products/quantities, order type, tables, riders.

Financial reports: gross/net sales, discounts, refunds, collections, payment methods, internal wallet sender numbers (authorized internal report only), purchases, supplier balances, expenses, cash/shift summaries, stock movement, gross/net profit and margins only when cost data is sufficient.

Charts must support date filters and drill-down to source transactions. Distinguish cash flow from profit metrics.

## 11. Printing and shortcuts
- Target 58mm/80mm thermal printers and A4 reports where supported.
- Configure printers per function; print test page.
- Print work is asynchronous and cannot freeze cashier UI.
- Persist print job; track queued, dispatched, confirmed/printed when observable, failed.
- Reprint does not duplicate order/payment.
- Keyboard shortcuts proposed: F2 focus search, F3 new order, F5 accept and print, Ctrl/Cmd+P print selected document. Avoid intercepting keys while typing.

## 12. Export, backup, restore
- Local PDF and XLSX export.
- Select destination folder or removable drive.
- Manual and scheduled backups, retention, integrity check, optional encryption.
- Optional off-device/cloud copy.
- Restore validates archive and creates pre-restore backup.
- Restore drill is a release gate.

## 13. Offline-first and sync
- Local DB is source of truth.
- Order creation, payment capture, print queue, local reports, export, backup work offline.
- Multiple terminals use a local host.
- Sync uses transactional outbox, stable event IDs, idempotency keys, retry/backoff, server deduplication, explicit conflict handling.
- No blind last-write-wins for financial state.
- Mobile manager sees last synced view and timestamp.
- Cloud/license outage cannot abruptly stop local sales.

## 14. Performance and UI quality
Cashier workflow has highest priority. Print, report, export, backup, sync run in background.
Initial targets (not guarantees): add product/refresh total P95 <100ms; local search P95 <200ms; durable order save P95 <500ms. Measure print enqueue separately from physical output.
Consistent design system, Arabic RTL/English LTR, desktop-first layouts, mouse/keyboard/optional touch, clear failure and offline states.

## 15. Licensing and distribution
Monthly, annual, and perpetual licenses. Define device/update/support terms. Signed license payloads; signing private key never ships in app. Offline activation/grace policy. Expiration must not delete customer data. Build/test separate signed Windows/macOS installers and upgrades.

## 16. Out of scope for v1
Full ERP, HR/payroll, marketplace, advanced CRM/loyalty, e-commerce storefront, GPS tracking, and integrations without a selected provider/workflow.

## 17. Acceptance criteria
1. Complete local sale works without internet.
2. Accepted orders cannot be hard-deleted.
3. Prep and payment states are independent.
4. Retries cannot duplicate order/payment.
5. Customer paper shows order type, customer name, date/time, products, quantities, prices, total.
6. Sender phone number is absent from all customer/preparation printouts.
7. Reports reconcile to source transactions.
8. PDF/XLSX work offline and render Arabic correctly.
9. Backup restore is verified on a clean environment.
10. Printer failure does not freeze UI or lose order.
11. Multi-terminal concurrency and LAN recovery are tested.
12. Performance/soak gates pass on agreed reference hardware.
13. Windows/macOS installers, updates, printers, backup paths verified.
14. No feature is declared production-ready without code, tests, and evidence.
