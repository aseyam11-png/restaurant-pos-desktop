# Security Policy

Do not publish exploitable vulnerabilities, credentials, customer records, wallet information, or license signing material in public issues. Coordinate disclosure privately with the repository owner.

## Sensitive data rules
- Never commit API keys, passwords, wallet PINs, OTPs, signing keys, or production dumps.
- Never print wallet-transfer sender phone number on customer or kitchen documents.
- Restrict sender phone number to authorized internal reports and protect it as personal data.
- Hash passwords with a dedicated password hashing algorithm such as Argon2id.
- Keep license signing private key off customer devices and out of this repository.
- Encrypt backup archives and protect recovery keys.
- Enforce authorization on APIs, not only by hiding UI controls.

Authentication, licensing, payments, backup/restore, sync, LAN networking, and customer-data changes require a threat review and regression tests.
