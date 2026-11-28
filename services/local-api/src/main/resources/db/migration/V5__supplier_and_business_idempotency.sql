CREATE UNIQUE INDEX uq_suppliers_name_lower_active ON suppliers(lower(name)) WHERE active=TRUE;
CREATE TABLE purchase_idempotency (
 idempotency_key VARCHAR(128) PRIMARY KEY,request_hash CHAR(64) NOT NULL,purchase_id UUID UNIQUE REFERENCES purchases(id) ON DELETE RESTRICT,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE expense_idempotency (
 idempotency_key VARCHAR(128) PRIMARY KEY,request_hash CHAR(64) NOT NULL,expense_id UUID UNIQUE REFERENCES expenses(id) ON DELETE RESTRICT,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
