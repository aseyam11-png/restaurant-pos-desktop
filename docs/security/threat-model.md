# Initial Threat Model

## Assets
Order/payment/purchase/expense/inventory/shift records; customer name/contact; internal wallet transfer sender phone; local DB and backups; owner/manager credentials; license and sync credentials; branding assets.

## Trust boundaries
Desktop UI → local API; terminals → LAN host; local DB → cloud sync; mobile manager → cloud API; backup archives → USB/disk/cloud; app → OS printer drivers/spooler.

## Threats and controls

| Threat | Required control |
|---|---|
| UI tampers with price/payment state | Server-side validation; immutable line snapshots; audit |
| Duplicate order/payment on retry | Idempotency keys and unique constraints |
| Cross-customer cloud data access | Server-side tenant authorization and isolation tests |
| Sender phone leaks to receipt | Separate internal report DTO; print privacy tests |
| Password theft | Argon2id, rate limits, secure session storage |
| Local API abuse | Loopback binding where applicable; local auth and origin/CSRF controls |
| LAN interception | Authenticated encrypted transport and device enrollment |
| Backup theft | Authenticated encryption; safe recovery-key UX |
| License tampering | Signed payload; private signing key never shipped |
| Malicious update | Signed artifacts and update verification |
| Printer failure loses order | Durable print queue with explicit state |
| Sync replay/duplicates | Stable event IDs and idempotent ingestion |

## Privacy
- Never store wallet PIN, password, or OTP.
- Store only sender phone number required by internal report.
- Restrict it to authorized internal views.
- Never print it on customer/preparation paper.
- Public repository contains synthetic examples only.

## Open questions
Local device authentication; encryption-at-rest and recovery UX; tenant model in cloud; retention policy for customer phone numbers; Windows/macOS secure credential storage.
