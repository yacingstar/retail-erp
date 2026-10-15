-- Reports module + cost snapshot on sales lines (for accurate profit).

-- The product's cost at the moment of the sale. Without it, profit of old sales would change every time a
-- product's cost price changes. Existing lines get today's cost (the best information we have).
ALTER TABLE sales_order_lines ADD COLUMN unit_cost numeric(12, 2);
UPDATE sales_order_lines l SET unit_cost = p.cost_price FROM products p WHERE p.id = l.product_id;
ALTER TABLE sales_order_lines ALTER COLUMN unit_cost SET NOT NULL;
ALTER TABLE sales_order_lines ADD CONSTRAINT sales_order_lines_unit_cost_check CHECK (unit_cost >= 0);

-- Speeds up reports, which always filter confirmed sales by date.
CREATE INDEX sales_orders_report_idx ON sales_orders (tenant_id, order_date) WHERE status = 'CONFIRMED';

INSERT INTO plan_modules (plan_code, module) VALUES ('BUSINESS', 'REPORTS'), ('PRO', 'REPORTS');
