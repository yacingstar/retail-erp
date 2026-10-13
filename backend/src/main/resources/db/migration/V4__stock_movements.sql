-- The core domain rule: stock is NEVER edited. Every change is a new row here, and the stock level of a
-- product in a warehouse is SUM(quantity). Positive = stock in, negative = stock out.
CREATE TABLE stock_movements (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenants (id),
    product_id     uuid         NOT NULL,
    warehouse_id   uuid         NOT NULL,
    quantity       integer      NOT NULL CHECK (quantity <> 0),
    type           varchar(30)  NOT NULL CHECK (type IN ('PURCHASE_RECEIPT', 'SALE', 'SALE_RETURN', 'ADJUSTMENT', 'TRANSFER_IN', 'TRANSFER_OUT')),
    reference_type varchar(30),
    reference_id   uuid,
    note           varchar(500),
    created_by     uuid,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES warehouses (tenant_id, id)
);
CREATE INDEX stock_movements_level_idx ON stock_movements (tenant_id, product_id, warehouse_id);
CREATE INDEX stock_movements_created_idx ON stock_movements (tenant_id, created_at DESC);
SELECT enable_tenant_rls('stock_movements');

-- Append-only, enforced twice: the app role has no UPDATE/DELETE privilege, and a trigger rejects them
-- for everyone else (including the owner).
REVOKE UPDATE, DELETE, TRUNCATE ON stock_movements FROM ${app_db_user};

CREATE FUNCTION stock_movements_append_only() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only: record a correcting movement instead of % ', TG_OP;
END
$$;
CREATE TRIGGER stock_movements_no_update_delete
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION stock_movements_append_only();
