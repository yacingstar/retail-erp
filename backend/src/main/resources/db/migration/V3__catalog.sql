-- (tenant_id, id) unique constraints let child tables use COMPOSITE foreign keys, so the database itself
-- guarantees that a row can never reference a row of another tenant.
CREATE TABLE products (
    id            uuid PRIMARY KEY,
    tenant_id     uuid           NOT NULL REFERENCES tenants (id),
    sku           varchar(64)    NOT NULL,
    name          varchar(200)   NOT NULL,
    description   text,
    unit          varchar(20)    NOT NULL DEFAULT 'pcs',
    sale_price    numeric(12, 2) NOT NULL DEFAULT 0 CHECK (sale_price >= 0),
    cost_price    numeric(12, 2) NOT NULL DEFAULT 0 CHECK (cost_price >= 0),
    tax_rate      numeric(5, 2)  NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    reorder_level integer        NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    active        boolean        NOT NULL DEFAULT true,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    version       bigint         NOT NULL DEFAULT 0,
    CONSTRAINT products_tenant_sku_uk UNIQUE (tenant_id, sku),
    CONSTRAINT products_tenant_id_uk UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('products');

CREATE TABLE warehouses (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    code       varchar(32)  NOT NULL,
    name       varchar(200) NOT NULL,
    address    text,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT warehouses_tenant_code_uk UNIQUE (tenant_id, code),
    CONSTRAINT warehouses_tenant_id_uk UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('warehouses');
