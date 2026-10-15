-- Supplier bills (what we owe suppliers) and supplier payments.

CREATE TABLE supplier_bills (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid           NOT NULL REFERENCES tenants (id),
    number             varchar(30)    NOT NULL,
    supplier_reference varchar(100),
    supplier_id        uuid           NOT NULL,
    purchase_order_id  uuid,
    status             varchar(20)    NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'PARTIALLY_PAID', 'PAID', 'VOID')),
    bill_date          date           NOT NULL,
    due_date           date,
    description        text,
    total              numeric(14, 2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    amount_paid        numeric(14, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    approved_at        timestamptz,
    created_by         uuid,
    created_at         timestamptz    NOT NULL DEFAULT now(),
    updated_at         timestamptz    NOT NULL DEFAULT now(),
    version            bigint         NOT NULL DEFAULT 0,
    CONSTRAINT supplier_bills_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT supplier_bills_tenant_id_uk UNIQUE (tenant_id, id),
    -- One bill per purchase order (receiving it twice can't bill it twice).
    CONSTRAINT supplier_bills_po_uk UNIQUE (purchase_order_id),
    CONSTRAINT supplier_bills_paid_le_total CHECK (amount_paid <= total),
    FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id),
    FOREIGN KEY (tenant_id, purchase_order_id) REFERENCES purchase_orders (tenant_id, id)
);
CREATE INDEX supplier_bills_supplier_idx ON supplier_bills (tenant_id, supplier_id);
CREATE INDEX supplier_bills_status_idx ON supplier_bills (tenant_id, status);
SELECT enable_tenant_rls('supplier_bills');

CREATE TABLE supplier_bill_lines (
    id          uuid PRIMARY KEY,
    tenant_id   uuid           NOT NULL REFERENCES tenants (id),
    bill_id     uuid           NOT NULL,
    line_no     integer        NOT NULL,
    description varchar(300)   NOT NULL,
    quantity    numeric(12, 3) NOT NULL CHECK (quantity > 0),
    unit_price  numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    line_total  numeric(14, 2) NOT NULL,
    FOREIGN KEY (tenant_id, bill_id) REFERENCES supplier_bills (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX supplier_bill_lines_bill_idx ON supplier_bill_lines (bill_id);
SELECT enable_tenant_rls('supplier_bill_lines');

-- Money paid to suppliers. Append-only: a wrong payment is never edited (void the bill only if nothing was paid).
CREATE TABLE supplier_payments (
    id         uuid PRIMARY KEY,
    tenant_id  uuid           NOT NULL REFERENCES tenants (id),
    bill_id    uuid           NOT NULL,
    amount     numeric(14, 2) NOT NULL CHECK (amount > 0),
    method     varchar(20)    NOT NULL CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CHEQUE', 'CARD', 'OTHER')),
    reference  varchar(100),
    paid_at    timestamptz    NOT NULL,
    created_by uuid,
    created_at timestamptz    NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, bill_id) REFERENCES supplier_bills (tenant_id, id)
);
CREATE INDEX supplier_payments_bill_idx ON supplier_payments (bill_id);
SELECT enable_tenant_rls('supplier_payments');
REVOKE UPDATE, DELETE, TRUNCATE ON supplier_payments FROM ${app_db_user};

INSERT INTO plan_modules (plan_code, module) VALUES ('BUSINESS', 'SUPPLIER_BILLS'), ('PRO', 'SUPPLIER_BILLS');
