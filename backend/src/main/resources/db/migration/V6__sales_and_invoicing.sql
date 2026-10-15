CREATE TABLE customers (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    name       varchar(200) NOT NULL,
    email      varchar(320),
    phone      varchar(50),
    address    text,
    notes      text,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT customers_tenant_id_uk UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('customers');

CREATE TABLE sales_orders (
    id           uuid PRIMARY KEY,
    tenant_id    uuid           NOT NULL REFERENCES tenants (id),
    number       varchar(30)    NOT NULL,
    customer_id  uuid,
    warehouse_id uuid           NOT NULL,
    status       varchar(20)    NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    order_date   date           NOT NULL,
    confirmed_at timestamptz,
    notes        text,
    subtotal     numeric(14, 2) NOT NULL DEFAULT 0,
    tax_total    numeric(14, 2) NOT NULL DEFAULT 0,
    total        numeric(14, 2) NOT NULL DEFAULT 0,
    created_by   uuid,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    updated_at   timestamptz    NOT NULL DEFAULT now(),
    version      bigint         NOT NULL DEFAULT 0,
    CONSTRAINT sales_orders_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT sales_orders_tenant_id_uk UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES warehouses (tenant_id, id)
);
CREATE INDEX sales_orders_status_idx ON sales_orders (tenant_id, status);
SELECT enable_tenant_rls('sales_orders');

CREATE TABLE sales_order_lines (
    id             uuid PRIMARY KEY,
    tenant_id      uuid           NOT NULL REFERENCES tenants (id),
    sales_order_id uuid           NOT NULL,
    line_no        integer        NOT NULL,
    product_id     uuid           NOT NULL,
    quantity       integer        NOT NULL CHECK (quantity > 0),
    unit_price     numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    tax_rate       numeric(5, 2)  NOT NULL CHECK (tax_rate >= 0),
    line_subtotal  numeric(14, 2) NOT NULL,
    line_tax       numeric(14, 2) NOT NULL,
    line_total     numeric(14, 2) NOT NULL,
    FOREIGN KEY (tenant_id, sales_order_id) REFERENCES sales_orders (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id)
);
CREATE INDEX sales_order_lines_order_idx ON sales_order_lines (sales_order_id);
SELECT enable_tenant_rls('sales_order_lines');

CREATE TABLE invoices (
    id             uuid PRIMARY KEY,
    tenant_id      uuid           NOT NULL REFERENCES tenants (id),
    number         varchar(30)    NOT NULL,
    sales_order_id uuid           NOT NULL,
    customer_id    uuid,
    status         varchar(20)    NOT NULL CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    issue_date     date           NOT NULL,
    due_date       date           NOT NULL,
    subtotal       numeric(14, 2) NOT NULL,
    tax_total      numeric(14, 2) NOT NULL,
    total          numeric(14, 2) NOT NULL,
    amount_paid    numeric(14, 2) NOT NULL DEFAULT 0,
    created_at     timestamptz    NOT NULL DEFAULT now(),
    updated_at     timestamptz    NOT NULL DEFAULT now(),
    version        bigint         NOT NULL DEFAULT 0,
    CONSTRAINT invoices_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT invoices_sales_order_uk UNIQUE (sales_order_id),
    CONSTRAINT invoices_tenant_id_uk UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, sales_order_id) REFERENCES sales_orders (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);
CREATE INDEX invoices_status_idx ON invoices (tenant_id, status);
SELECT enable_tenant_rls('invoices');

CREATE TABLE payments (
    id         uuid PRIMARY KEY,
    tenant_id  uuid           NOT NULL REFERENCES tenants (id),
    invoice_id uuid           NOT NULL,
    amount     numeric(14, 2) NOT NULL CHECK (amount > 0),
    method     varchar(20)    NOT NULL CHECK (method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    reference  varchar(100),
    paid_at    timestamptz    NOT NULL DEFAULT now(),
    created_by uuid,
    created_at timestamptz    NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES invoices (tenant_id, id)
);
CREATE INDEX payments_invoice_idx ON payments (invoice_id);
SELECT enable_tenant_rls('payments');
