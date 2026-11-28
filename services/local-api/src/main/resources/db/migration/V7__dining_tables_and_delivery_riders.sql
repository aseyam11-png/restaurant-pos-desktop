CREATE TABLE dining_zones (
 id UUID PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order>=0),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE dining_tables (
 id UUID PRIMARY KEY, zone_id UUID NOT NULL REFERENCES dining_zones(id) ON DELETE RESTRICT,
 table_number INTEGER NOT NULL CHECK(table_number>0), label VARCHAR(40) NOT NULL,
 seats SMALLINT NOT NULL DEFAULT 2 CHECK(seats BETWEEN 1 AND 50),
 status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' CHECK(status IN('AVAILABLE','RESERVED','OCCUPIED','OUT_OF_SERVICE')),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_dining_zone_table UNIQUE(zone_id,table_number)
);
CREATE INDEX ix_dining_tables_zone_status ON dining_tables(zone_id,status,table_number);
CREATE TABLE delivery_riders (
 id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL,phone VARCHAR(32) NOT NULL,notes VARCHAR(240),
 active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_delivery_riders_active_name ON delivery_riders(active,name);
ALTER TABLE orders
 ADD COLUMN dining_table_id UUID REFERENCES dining_tables(id) ON DELETE RESTRICT,
 ADD COLUMN delivery_rider_id UUID REFERENCES delivery_riders(id) ON DELETE RESTRICT,
 ADD COLUMN delivery_address VARCHAR(240),
 ADD COLUMN delivery_phone VARCHAR(32);
CREATE INDEX ix_orders_dining_table_active ON orders(dining_table_id,preparation_status) WHERE dining_table_id IS NOT NULL;
CREATE INDEX ix_orders_delivery_rider ON orders(delivery_rider_id,created_at DESC) WHERE delivery_rider_id IS NOT NULL;
