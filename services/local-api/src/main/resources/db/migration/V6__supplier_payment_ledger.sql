ALTER TABLE purchases
    ADD COLUMN payment_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID'
    CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID'));

UPDATE purchases
SET payment_status = CASE
    WHEN total > 0 AND paid_amount >= total THEN 'PAID'
    WHEN paid_amount > 0 THEN 'PARTIALLY_PAID'
    ELSE 'UNPAID'
END;

CREATE TABLE supplier_payments (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id) ON DELETE RESTRICT,
    method VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'WALLET', 'BANK_CARD')),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    cash_shift_id UUID REFERENCES cash_shifts(id) ON DELETE RESTRICT,
    paid_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by VARCHAR(80) NOT NULL,
    CONSTRAINT ck_supplier_payment_cash_shift CHECK (method <> 'CASH' OR cash_shift_id IS NOT NULL)
);
CREATE INDEX ix_supplier_payments_purchase ON supplier_payments(purchase_id, paid_at DESC);

INSERT INTO supplier_payments(id, purchase_id, method, amount, currency, cash_shift_id, paid_at, recorded_by)
SELECT gen_random_uuid(), id, payment_method, paid_amount, currency, cash_shift_id, purchased_at, created_by
FROM purchases
WHERE paid_amount > 0 AND payment_method IS NOT NULL;

CREATE TABLE supplier_payment_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    payment_id UUID UNIQUE REFERENCES supplier_payments(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
