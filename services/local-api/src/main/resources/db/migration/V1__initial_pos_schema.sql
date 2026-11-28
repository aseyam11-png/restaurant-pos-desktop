CREATE SEQUENCE product_number_seq START WITH 10001 INCREMENT BY 1;
CREATE SEQUENCE order_number_seq START WITH 1001 INCREMENT BY 1;

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    product_no BIGINT NOT NULL DEFAULT nextval('product_number_seq') UNIQUE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    unit VARCHAR(24) NOT NULL DEFAULT 'piece',
    pricing_mode VARCHAR(24) NOT NULL CHECK (pricing_mode IN ('FIXED', 'ASK_AT_SALE')),
    price NUMERIC(14,2),
    stock_tracked BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_products_price CHECK (
      (pricing_mode = 'FIXED' AND price IS NOT NULL AND price > 0)
      OR (pricing_mode = 'ASK_AT_SALE' AND price IS NULL)
    )
);
CREATE INDEX ix_products_category_active ON products(category_id, active, name);
CREATE INDEX ix_products_name_lower ON products(lower(name));
CREATE INDEX ix_products_number ON products(product_no);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_no BIGINT NOT NULL DEFAULT nextval('order_number_seq') UNIQUE,
    customer_name VARCHAR(80) NOT NULL,
    order_type VARCHAR(20) NOT NULL CHECK (order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY')),
    preparation_status VARCHAR(24) NOT NULL DEFAULT 'NEW'
      CHECK (preparation_status IN ('NEW', 'IN_PREPARATION', 'READY', 'DELIVERED', 'CANCELLED')),
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID'
      CHECK (payment_status IN ('UNPAID', 'PENDING_CONFIRMATION', 'PARTIALLY_PAID', 'PAID', 'REFUNDED')),
    subtotal NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80) NOT NULL DEFAULT 'LOCAL_OPERATOR',
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(240),
    CONSTRAINT ck_orders_amounts CHECK (
      subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND total >= 0
      AND total = subtotal - discount_amount + tax_amount
    )
);
CREATE INDEX ix_orders_created_at ON orders(created_at DESC);
CREATE INDEX ix_orders_preparation_status ON orders(preparation_status, created_at DESC);

CREATE TABLE order_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    order_id UUID UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_no_snapshot BIGINT NOT NULL,
    product_name_snapshot VARCHAR(120) NOT NULL,
    unit_snapshot VARCHAR(24) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(14,2) NOT NULL CHECK (line_total >= 0)
);
CREATE INDEX ix_order_lines_order_id ON order_lines(order_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    method VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'WALLET', 'BANK_CARD')),
    status VARCHAR(32) NOT NULL CONSTRAINT ck_payments_status CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'REFUNDED')),
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    sender_phone VARCHAR(24),
    external_reference VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    confirmed_by VARCHAR(80),
    CONSTRAINT ck_payments_sender_phone CHECK (method = 'WALLET' OR sender_phone IS NULL)
);
CREATE INDEX ix_payments_order_id ON payments(order_id);
CREATE INDEX ix_payments_created_at ON payments(created_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    actor VARCHAR(80) NOT NULL,
    reason VARCHAR(240),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_aggregate ON audit_events(aggregate_type, aggregate_id, occurred_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pending ON outbox_events(next_attempt_at, created_at) WHERE published_at IS NULL;

CREATE TABLE print_jobs (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    document_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER_ORDER' CHECK (document_type IN ('CUSTOMER_ORDER', 'KITCHEN_TICKET')),
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'DISPATCHED', 'PRINTED', 'FAILED')),
    is_reprint BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(500)
);
CREATE INDEX ix_print_jobs_queue ON print_jobs(status, created_at);
