-- HR and payroll (Algeria): employee records, monthly payroll runs and payslips.

CREATE TABLE employees (
    id                uuid PRIMARY KEY,
    tenant_id         uuid           NOT NULL REFERENCES tenants (id),
    full_name         varchar(150)   NOT NULL,
    job_title         varchar(100),
    contract_type     varchar(20)    NOT NULL CHECK (contract_type IN ('CDI', 'CDD', 'OTHER')),
    hire_date         date           NOT NULL,
    end_date          date,
    nin               varchar(30),   -- Numéro d'identification national
    nss               varchar(30),   -- Numéro de sécurité sociale (CNAS)
    -- Monthly gross salary; subject to CNAS and IRG.
    base_salary       numeric(12, 2) NOT NULL CHECK (base_salary >= 0),
    -- Monthly panier + transport allowances: not subject to CNAS, subject to IRG.
    monthly_allowance numeric(12, 2) NOT NULL DEFAULT 0 CHECK (monthly_allowance >= 0),
    phone             varchar(40),
    bank_account      varchar(40),   -- RIB or CCP number for transfers
    notes             text,
    active            boolean        NOT NULL DEFAULT true,
    created_at        timestamptz    NOT NULL DEFAULT now(),
    updated_at        timestamptz    NOT NULL DEFAULT now(),
    version           bigint         NOT NULL DEFAULT 0,
    CONSTRAINT employees_tenant_id_uk UNIQUE (tenant_id, id),
    CONSTRAINT employees_dates CHECK (end_date IS NULL OR end_date >= hire_date)
);
CREATE UNIQUE INDEX employees_tenant_nss_uk ON employees (tenant_id, nss) WHERE nss IS NOT NULL;
SELECT enable_tenant_rls('employees');

-- One payroll run per month: DRAFT (payslips can be adjusted) → APPROVED (posted to accounting) → PAID.
CREATE TABLE payroll_runs (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid           NOT NULL REFERENCES tenants (id),
    number              varchar(30)    NOT NULL,
    period_year         integer        NOT NULL CHECK (period_year BETWEEN 2000 AND 2100),
    period_month        integer        NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status              varchar(20)    NOT NULL CHECK (status IN ('DRAFT', 'APPROVED', 'PAID')),
    payment_method      varchar(20)    CHECK (payment_method IN ('CASH', 'BANK_TRANSFER')),
    total_gross         numeric(14, 2) NOT NULL DEFAULT 0,
    total_cnas_employee numeric(14, 2) NOT NULL DEFAULT 0,
    total_irg           numeric(14, 2) NOT NULL DEFAULT 0,
    total_net           numeric(14, 2) NOT NULL DEFAULT 0,
    total_cnas_employer numeric(14, 2) NOT NULL DEFAULT 0,
    approved_at         timestamptz,
    paid_at             timestamptz,
    created_by          uuid,
    created_at          timestamptz    NOT NULL DEFAULT now(),
    updated_at          timestamptz    NOT NULL DEFAULT now(),
    version             bigint         NOT NULL DEFAULT 0,
    CONSTRAINT payroll_runs_tenant_id_uk UNIQUE (tenant_id, id),
    CONSTRAINT payroll_runs_tenant_number_uk UNIQUE (tenant_id, number),
    CONSTRAINT payroll_runs_period_uk UNIQUE (tenant_id, period_year, period_month)
);
SELECT enable_tenant_rls('payroll_runs');

-- A payslip copies the employee's name and figures at the time: later edits to the employee don't change it.
CREATE TABLE payslips (
    id             uuid PRIMARY KEY,
    tenant_id      uuid           NOT NULL REFERENCES tenants (id),
    run_id         uuid           NOT NULL,
    employee_id    uuid           NOT NULL,
    employee_name  varchar(150)   NOT NULL,
    job_title      varchar(100),
    nss            varchar(30),
    base_salary    numeric(12, 2) NOT NULL CHECK (base_salary >= 0),
    bonus          numeric(12, 2) NOT NULL DEFAULT 0 CHECK (bonus >= 0),
    allowance      numeric(12, 2) NOT NULL DEFAULT 0 CHECK (allowance >= 0),
    gross          numeric(12, 2) NOT NULL,
    cnas_base      numeric(12, 2) NOT NULL,
    cnas_employee  numeric(12, 2) NOT NULL,
    taxable_income numeric(12, 2) NOT NULL,
    irg            numeric(12, 2) NOT NULL,
    net_pay        numeric(12, 2) NOT NULL,
    cnas_employer  numeric(12, 2) NOT NULL,
    CONSTRAINT payslips_run_employee_uk UNIQUE (run_id, employee_id),
    CONSTRAINT payslips_net CHECK (net_pay = gross - cnas_employee - irg),
    FOREIGN KEY (tenant_id, run_id) REFERENCES payroll_runs (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, employee_id) REFERENCES employees (tenant_id, id)
);
CREATE INDEX payslips_run_idx ON payslips (run_id);
CREATE INDEX payslips_employee_idx ON payslips (tenant_id, employee_id);
SELECT enable_tenant_rls('payslips');

INSERT INTO plan_modules (plan_code, module) VALUES ('PRO', 'HR_PAYROLL');
