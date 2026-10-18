-- Double-entry accounting (Algerian SCF chart of accounts), and VAT on supplier bills.

-- Chart of accounts, per tenant. Seeded with the SCF accounts the app posts to (see ChartOfAccounts.java).
CREATE TABLE accounts (
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    code       varchar(10)  NOT NULL,
    name       varchar(150) NOT NULL,
    type       varchar(10)  NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    system     boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, code)
);
SELECT enable_tenant_rls('accounts');

CREATE TABLE journal_entries (
    id          uuid PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenants (id),
    number      varchar(30)  NOT NULL,
    entry_date  date         NOT NULL,
    description varchar(300) NOT NULL,
    source_type varchar(30)  NOT NULL,
    source_id   uuid,
    created_by  uuid,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT journal_entries_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT journal_entries_tenant_id_uk UNIQUE (tenant_id, id)
);
CREATE INDEX journal_entries_date_idx ON journal_entries (tenant_id, entry_date);
CREATE INDEX journal_entries_source_idx ON journal_entries (source_type, source_id);
SELECT enable_tenant_rls('journal_entries');

CREATE TABLE journal_lines (
    id           uuid PRIMARY KEY,
    tenant_id    uuid           NOT NULL REFERENCES tenants (id),
    entry_id     uuid           NOT NULL,
    line_no      integer        NOT NULL,
    account_code varchar(10)    NOT NULL,
    debit        numeric(14, 2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit       numeric(14, 2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
    memo         varchar(200),
    -- Each line is either a debit or a credit, never both, never zero.
    CONSTRAINT journal_lines_one_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)),
    FOREIGN KEY (tenant_id, entry_id) REFERENCES journal_entries (tenant_id, id),
    FOREIGN KEY (tenant_id, account_code) REFERENCES accounts (tenant_id, code)
);
CREATE INDEX journal_lines_entry_idx ON journal_lines (entry_id);
CREATE INDEX journal_lines_account_idx ON journal_lines (tenant_id, account_code);
SELECT enable_tenant_rls('journal_lines');

-- The books are append-only: corrections are reversing entries, never edits.
REVOKE UPDATE, DELETE, TRUNCATE ON journal_entries, journal_lines FROM ${app_db_user};

-- Debits must equal credits for every entry. A DEFERRED constraint trigger runs at COMMIT, after all the lines of
-- the entry have been inserted (checking after each single line would always fail on the first one).
CREATE FUNCTION journal_entry_balanced() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    total_debit  numeric;
    total_credit numeric;
BEGIN
    SELECT coalesce(sum(debit), 0), coalesce(sum(credit), 0) INTO total_debit, total_credit
    FROM journal_lines WHERE entry_id = NEW.entry_id;
    IF total_debit <> total_credit THEN
        RAISE EXCEPTION 'Journal entry % is not balanced: debit % <> credit %', NEW.entry_id, total_debit, total_credit;
    END IF;
    RETURN NULL;
END
$$;
CREATE CONSTRAINT TRIGGER journal_lines_balanced
    AFTER INSERT ON journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION journal_entry_balanced();

-- VAT on supplier bills (needed for the VAT report), and the expense account of manual bills (rent, power...).
ALTER TABLE supplier_bill_lines
    ADD COLUMN tax_rate numeric(5, 2)  NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    ADD COLUMN line_tax numeric(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE supplier_bills
    ADD COLUMN subtotal        numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN tax_total       numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN expense_account varchar(10);
UPDATE supplier_bills SET subtotal = total;

INSERT INTO plan_modules (plan_code, module) VALUES ('PRO', 'ACCOUNTING');
