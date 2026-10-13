CREATE TABLE tenants (
    id         uuid PRIMARY KEY,
    name       varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);
-- A tenant can only see its own row.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenants USING (id = current_tenant_id()) WITH CHECK (id = current_tenant_id());

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenants (id),
    email         varchar(320) NOT NULL,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'CASHIER')),
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0
);
-- Emails are unique across ALL tenants, so login only needs an email + password.
CREATE UNIQUE INDEX users_email_uk ON users (lower(email));
CREATE INDEX users_tenant_idx ON users (tenant_id);
SELECT enable_tenant_rls('users');

-- Login happens BEFORE we know the tenant, so RLS would hide every user. This function runs with the
-- owner's rights (SECURITY DEFINER) and returns only the one row matching the email: a narrow, audited hole.
CREATE FUNCTION auth_find_user_by_email(p_email text)
    RETURNS TABLE (id uuid, tenant_id uuid, email varchar, password_hash varchar, full_name varchar, role varchar, enabled boolean)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT u.id, u.tenant_id, u.email, u.password_hash, u.full_name, u.role, u.enabled
    FROM users u
    WHERE lower(u.email) = lower(p_email)
$$;
REVOKE ALL ON FUNCTION auth_find_user_by_email(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_find_user_by_email(text) TO ${app_db_user};

-- Per-tenant counters for human-readable document numbers (PO-000001, SO-000001, INV-000001).
CREATE TABLE document_sequences (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    doc_type   varchar(20) NOT NULL,
    next_value bigint      NOT NULL,
    PRIMARY KEY (tenant_id, doc_type)
);
SELECT enable_tenant_rls('document_sequences');
