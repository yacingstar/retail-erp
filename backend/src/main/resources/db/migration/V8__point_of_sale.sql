-- Point of Sale: product categories (for the checkout tiles), discounts, and cash register shifts.

ALTER TABLE products ADD COLUMN category varchar(100);
CREATE INDEX products_category_idx ON products (tenant_id, category);

-- Discounts. Amounts: subtotal (before discount) - discount_total + tax_total = total.
ALTER TABLE sales_orders
    ADD COLUMN discount_percent numeric(5, 2)  NOT NULL DEFAULT 0 CHECK (discount_percent >= 0 AND discount_percent <= 100),
    ADD COLUMN discount_total   numeric(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_order_lines ADD COLUMN line_discount numeric(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN discount_total numeric(14, 2) NOT NULL DEFAULT 0;

-- A cashier's shift at a store: opened with a cash float, closed with a cash count.
CREATE TABLE pos_shifts (
    id            uuid PRIMARY KEY,
    tenant_id     uuid           NOT NULL REFERENCES tenants (id),
    warehouse_id  uuid           NOT NULL,
    status        varchar(10)    NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    opened_by     uuid           NOT NULL,
    opened_at     timestamptz    NOT NULL,
    opening_cash  numeric(14, 2) NOT NULL CHECK (opening_cash >= 0),
    closed_by     uuid,
    closed_at     timestamptz,
    expected_cash numeric(14, 2),
    counted_cash  numeric(14, 2) CHECK (counted_cash >= 0),
    notes         text,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    version       bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pos_shifts_tenant_id_uk UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES warehouses (tenant_id, id)
);
-- A cashier can have only one open shift at a time (partial unique index: only rows WHERE status = 'OPEN').
CREATE UNIQUE INDEX pos_shifts_one_open_per_user ON pos_shifts (tenant_id, opened_by) WHERE status = 'OPEN';
CREATE INDEX pos_shifts_opened_idx ON pos_shifts (tenant_id, opened_at DESC);
SELECT enable_tenant_rls('pos_shifts');

ALTER TABLE sales_orders ADD COLUMN pos_shift_id uuid;
ALTER TABLE sales_orders ADD FOREIGN KEY (tenant_id, pos_shift_id) REFERENCES pos_shifts (tenant_id, id);
CREATE INDEX sales_orders_shift_idx ON sales_orders (pos_shift_id);

-- The new module is part of Business and Pro (Starter can buy it as an add-on).
INSERT INTO plan_modules (plan_code, module) VALUES ('BUSINESS', 'POINT_OF_SALE'), ('PRO', 'POINT_OF_SALE');
