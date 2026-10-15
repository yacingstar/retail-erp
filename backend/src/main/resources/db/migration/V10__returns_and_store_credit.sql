-- Returns (credit notes), refunds and store credit.

-- New payment method: paying with store credit. New invoice status: fully settled by credit notes, nothing paid.
ALTER TABLE payments DROP CONSTRAINT payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_method_check
    CHECK (method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER', 'STORE_CREDIT'));
ALTER TABLE invoices DROP CONSTRAINT invoices_status_check;
ALTER TABLE invoices ADD CONSTRAINT invoices_status_check
    CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'CREDITED', 'VOID'));

-- What credit notes took off the invoice, and how much money was given back.
-- Outstanding = total − amount_paid − amount_credited. Refund due = amount_paid + amount_credited − total − amount_refunded.
ALTER TABLE invoices
    ADD COLUMN amount_credited numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN amount_refunded numeric(14, 2) NOT NULL DEFAULT 0;

-- Return lines point at the exact sales order line they return.
ALTER TABLE sales_order_lines ADD CONSTRAINT sales_order_lines_tenant_id_uk UNIQUE (tenant_id, id);

-- A return = a credit note. Immutable once created (a mistake is fixed by another document, never by editing).
CREATE TABLE sales_returns (
    id             uuid PRIMARY KEY,
    tenant_id      uuid           NOT NULL REFERENCES tenants (id),
    number         varchar(30)    NOT NULL,
    return_date    date           NOT NULL,
    sales_order_id uuid           NOT NULL,
    invoice_id     uuid           NOT NULL,
    customer_id    uuid,
    warehouse_id   uuid           NOT NULL,
    reason         text,
    subtotal       numeric(14, 2) NOT NULL,
    discount_total numeric(14, 2) NOT NULL,
    tax_total      numeric(14, 2) NOT NULL,
    total          numeric(14, 2) NOT NULL CHECK (total >= 0),
    refund_amount  numeric(14, 2) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    refund_method  varchar(20) CHECK (refund_method IN ('CASH', 'CARD', 'STORE_CREDIT')),
    pos_shift_id   uuid,
    created_by     uuid,
    created_at     timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT sales_returns_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT sales_returns_tenant_id_uk UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, sales_order_id) REFERENCES sales_orders (tenant_id, id),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES invoices (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES warehouses (tenant_id, id),
    FOREIGN KEY (tenant_id, pos_shift_id) REFERENCES pos_shifts (tenant_id, id)
);
CREATE INDEX sales_returns_order_idx ON sales_returns (sales_order_id);
CREATE INDEX sales_returns_date_idx ON sales_returns (tenant_id, return_date);
SELECT enable_tenant_rls('sales_returns');

CREATE TABLE sales_return_lines (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid           NOT NULL REFERENCES tenants (id),
    sales_return_id     uuid           NOT NULL,
    sales_order_line_id uuid           NOT NULL,
    product_id          uuid           NOT NULL,
    quantity            integer        NOT NULL CHECK (quantity > 0),
    restock             boolean        NOT NULL,
    unit_cost           numeric(12, 2) NOT NULL,
    line_subtotal       numeric(14, 2) NOT NULL,
    line_discount       numeric(14, 2) NOT NULL,
    line_tax            numeric(14, 2) NOT NULL,
    line_total          numeric(14, 2) NOT NULL,
    FOREIGN KEY (tenant_id, sales_return_id) REFERENCES sales_returns (tenant_id, id),
    FOREIGN KEY (tenant_id, sales_order_line_id) REFERENCES sales_order_lines (tenant_id, id),
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id)
);
CREATE INDEX sales_return_lines_return_idx ON sales_return_lines (sales_return_id);
CREATE INDEX sales_return_lines_order_line_idx ON sales_return_lines (sales_order_line_id);
SELECT enable_tenant_rls('sales_return_lines');
REVOKE UPDATE, DELETE, TRUNCATE ON sales_returns, sales_return_lines FROM ${app_db_user};

-- Store credit is a ledger, like stock: + when credit is given, − when it's spent. Balance = SUM(amount).
CREATE TABLE store_credit_entries (
    id           uuid PRIMARY KEY,
    tenant_id    uuid           NOT NULL REFERENCES tenants (id),
    customer_id  uuid           NOT NULL,
    amount       numeric(14, 2) NOT NULL CHECK (amount <> 0),
    reason       varchar(30)    NOT NULL CHECK (reason IN ('RETURN_REFUND', 'PAYMENT')),
    reference_id uuid,
    note         varchar(200),
    created_by   uuid,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);
CREATE INDEX store_credit_customer_idx ON store_credit_entries (tenant_id, customer_id);
SELECT enable_tenant_rls('store_credit_entries');
REVOKE UPDATE, DELETE, TRUNCATE ON store_credit_entries FROM ${app_db_user};

-- Returns are basic: every plan gets them.
INSERT INTO plan_modules (plan_code, module) VALUES ('STARTER', 'RETURNS'), ('BUSINESS', 'RETURNS'), ('PRO', 'RETURNS');
