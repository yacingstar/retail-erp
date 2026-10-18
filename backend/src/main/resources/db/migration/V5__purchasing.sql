CREATE TABLE suppliers (
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
    CONSTRAINT suppliers_tenant_id_uk UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('suppliers');

CREATE TABLE purchase_orders (
    id            uuid PRIMARY KEY,
    tenant_id     uuid           NOT NULL REFERENCES tenants (id),
    number        varchar(30)    NOT NULL,
    supplier_id   uuid           NOT NULL,
    warehouse_id  uuid           NOT NULL,
    status        varchar(20)    NOT NULL CHECK (status IN ('DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED')),
    order_date    date           NOT NULL,
    expected_date date,
    received_at   timestamptz,
    notes         text,
    total         numeric(14, 2) NOT NULL DEFAULT 0,
    created_by    uuid,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    version       bigint         NOT NULL DEFAULT 0,
    CONSTRAINT purchase_orders_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT purchase_orders_tenant_id_uk UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES warehouses (tenant_id, id)
);
CREATE INDEX purchase_orders_status_idx ON purchase_orders (tenant_id, status);
SELECT enable_tenant_rls('purchase_orders');

CREATE TABLE purchase_order_lines (
    id                uuid PRIMARY KEY,
    tenant_id         uuid           NOT NULL REFERENCES tenants (id),
    purchase_order_id uuid           NOT NULL,
    line_no           integer        NOT NULL,
    product_id        uuid           NOT NULL,
    quantity          integer        NOT NULL CHECK (quantity > 0),
    unit_cost         numeric(12, 2) NOT NULL CHECK (unit_cost >= 0),
    line_total        numeric(14, 2) NOT NULL,
    FOREIGN KEY (tenant_id, purchase_order_id) REFERENCES purchase_orders (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id)
);
CREATE INDEX purchase_order_lines_order_idx ON purchase_order_lines (purchase_order_id);
SELECT enable_tenant_rls('purchase_order_lines');
