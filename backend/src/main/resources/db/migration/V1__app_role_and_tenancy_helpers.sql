-- Runs as the admin/owner role (spring.flyway.user). Creates the application role the backend connects with.
-- The app role does NOT own the tables, so Postgres Row-Level Security (RLS) applies to it.
-- (Table owners and superusers bypass RLS, which is exactly why the app must not connect as the owner.)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_db_user}') THEN
        CREATE ROLE ${app_db_user} LOGIN PASSWORD '${app_db_password}' NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    ELSE
        ALTER ROLE ${app_db_user} WITH LOGIN PASSWORD '${app_db_password}' NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO ${app_db_user};
-- Every table/sequence created later by this (owner) role is usable by the app role.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_db_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${app_db_user};

-- The backend sets this per connection: SELECT set_config('app.tenant_id', '<uuid>', false).
-- Unset or empty means "no tenant", which makes every RLS policy match zero rows (fail closed).
CREATE FUNCTION current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

-- Helper used by later migrations: turn on RLS for a tenant-owned table.
CREATE FUNCTION enable_tenant_rls(tbl regclass) RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())', tbl);
END
$$;
