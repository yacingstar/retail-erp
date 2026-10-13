-- Plans, subscriptions and add-ons: which modules and limits each tenant gets.

-- Plan catalogue (global, not tenant-owned). NULL limit = unlimited.
CREATE TABLE plans (
    code           varchar(20)  PRIMARY KEY,
    name           varchar(100) NOT NULL,
    max_users      integer CHECK (max_users > 0),
    max_warehouses integer CHECK (max_warehouses > 0),
    sort_order     integer      NOT NULL
);

CREATE TABLE plan_modules (
    plan_code varchar(20) NOT NULL REFERENCES plans (code) ON DELETE CASCADE,
    module    varchar(30) NOT NULL,
    PRIMARY KEY (plan_code, module)
);

INSERT INTO plans (code, name, max_users, max_warehouses, sort_order) VALUES
    ('STARTER',  'Starter',  2,    1,    1),
    ('BUSINESS', 'Business', 10,   3,    2),
    ('PRO',      'Pro',      NULL, NULL, 3);

INSERT INTO plan_modules (plan_code, module) VALUES
    ('STARTER',  'INVENTORY'), ('STARTER',  'SALES'), ('STARTER',  'INVOICING'),
    ('BUSINESS', 'INVENTORY'), ('BUSINESS', 'SALES'), ('BUSINESS', 'INVOICING'), ('BUSINESS', 'PURCHASING'),
    ('PRO',      'INVENTORY'), ('PRO',      'SALES'), ('PRO',      'INVOICING'), ('PRO',      'PURCHASING');

-- The app may only READ the catalogue.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON plans, plan_modules FROM ${app_db_user};

-- One subscription per tenant. While trial_ends_at is in the future, the tenant gets trial_plan_code instead of plan_code.
CREATE TABLE tenant_subscriptions (
    tenant_id       uuid        PRIMARY KEY REFERENCES tenants (id),
    plan_code       varchar(20) NOT NULL REFERENCES plans (code),
    trial_plan_code varchar(20) REFERENCES plans (code),
    trial_ends_at   timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now()
);
SELECT enable_tenant_rls('tenant_subscriptions');

-- Extra modules bought on top of the plan.
CREATE TABLE tenant_addons (
    tenant_id uuid        NOT NULL REFERENCES tenants (id),
    module    varchar(30) NOT NULL,
    PRIMARY KEY (tenant_id, module)
);
SELECT enable_tenant_rls('tenant_addons');

-- A tenant can never change its own plan, even through a bug: the app role may only create the subscription at
-- registration and read it. Changes go through the platform functions below.
REVOKE UPDATE, DELETE, TRUNCATE ON tenant_subscriptions FROM ${app_db_user};
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON tenant_addons FROM ${app_db_user};

-- Existing tenants (created before this migration) start on Starter with a 14-day Pro trial.
INSERT INTO tenant_subscriptions (tenant_id, plan_code, trial_plan_code, trial_ends_at)
SELECT id, 'STARTER', 'PRO', now() + interval '14 days' FROM tenants;

-- Platform administrators (the people running the SaaS, not shop staff). Not tenant-owned, so no RLS.
CREATE TABLE platform_admins (
    id            uuid PRIMARY KEY,
    email         varchar(320) NOT NULL,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX platform_admins_email_uk ON platform_admins (lower(email));

-- Cross-tenant operations for the platform admin panel. SECURITY DEFINER = runs with the owner's rights, so RLS
-- doesn't hide other tenants. The backend only calls these from endpoints restricted to PLATFORM_ADMIN.
CREATE FUNCTION platform_tenants()
    RETURNS TABLE (id uuid, name varchar, created_at timestamptz, plan_code varchar, trial_plan_code varchar,
                   trial_ends_at timestamptz, addons varchar[], user_count bigint, warehouse_count bigint)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT t.id, t.name, t.created_at, s.plan_code, s.trial_plan_code, s.trial_ends_at,
           coalesce((SELECT array_agg(a.module ORDER BY a.module) FROM tenant_addons a WHERE a.tenant_id = t.id), '{}'),
           (SELECT count(*) FROM users u WHERE u.tenant_id = t.id AND u.enabled),
           (SELECT count(*) FROM warehouses w WHERE w.tenant_id = t.id AND w.active)
    FROM tenants t JOIN tenant_subscriptions s ON s.tenant_id = t.id
    ORDER BY t.created_at DESC
$$;

CREATE FUNCTION platform_set_subscription(p_tenant uuid, p_plan varchar, p_trial_ends timestamptz, p_addons varchar[])
    RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public
AS $$
BEGIN
    UPDATE tenant_subscriptions
    SET plan_code = p_plan,
        trial_ends_at = p_trial_ends,
        trial_plan_code = CASE WHEN p_trial_ends IS NULL THEN NULL ELSE coalesce(trial_plan_code, 'PRO') END,
        updated_at = now()
    WHERE tenant_id = p_tenant;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Unknown tenant %', p_tenant USING ERRCODE = 'no_data_found';
    END IF;
    DELETE FROM tenant_addons WHERE tenant_id = p_tenant;
    INSERT INTO tenant_addons (tenant_id, module) SELECT DISTINCT p_tenant, m FROM unnest(coalesce(p_addons, '{}')) AS m;
END
$$;

REVOKE ALL ON FUNCTION platform_tenants() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_set_subscription(uuid, varchar, timestamptz, varchar[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform_tenants() TO ${app_db_user};
GRANT EXECUTE ON FUNCTION platform_set_subscription(uuid, varchar, timestamptz, varchar[]) TO ${app_db_user};
