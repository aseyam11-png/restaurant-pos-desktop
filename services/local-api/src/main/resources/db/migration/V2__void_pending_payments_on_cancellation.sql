ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'REFUNDED', 'VOIDED'));
