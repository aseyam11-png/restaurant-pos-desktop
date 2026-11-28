CREATE UNIQUE INDEX uq_orders_one_active_per_dining_table
    ON orders(dining_table_id)
    WHERE dining_table_id IS NOT NULL
      AND preparation_status NOT IN ('DELIVERED', 'CANCELLED');
