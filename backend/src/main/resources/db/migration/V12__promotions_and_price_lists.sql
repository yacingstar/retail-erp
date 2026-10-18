-- Price lists (customer groups) and promotions.

CREATE TABLE price_lists (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT price_lists_tenant_name_uk UNIQUE (tenant_id, name),
    CONSTRAINT price_lists_tenant_id_uk UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('price_lists');

CREATE TABLE price_list_items (
    id            uuid PRIMARY KEY,
    tenant_id     uuid           NOT NULL REFERENCES tenants (id),
    price_list_id uuid           NOT NULL,
    product_id    uuid           NOT NULL,
    price         numeric(12, 2) NOT NULL CHECK (price >= 0),
    CONSTRAINT price_list_items_uk UNIQUE (price_list_id, product_id),
    FOREIGN KEY (tenant_id, price_list_id) REFERENCES price_lists (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id)
);
SELECT enable_tenant_rls('price_list_items');

-- A customer belongs to at most one price list (their "customer group").
ALTER TABLE customers ADD COLUMN price_list_id uuid;
ALTER TABLE customers ADD FOREIGN KEY (tenant_id, price_list_id) REFERENCES price_lists (tenant_id, id);

CREATE TABLE promotions (
    id               uuid PRIMARY KEY,
    tenant_id        uuid           NOT NULL REFERENCES tenants (id),
    name             varchar(150)   NOT NULL,
    type             varchar(30)    NOT NULL CHECK (type IN ('PERCENT_OFF', 'AMOUNT_OFF', 'BUY_X_GET_Y', 'ORDER_PERCENT_OFF')),
    value            numeric(12, 2) NOT NULL DEFAULT 0 CHECK (value >= 0),
    buy_qty          integer CHECK (buy_qty > 0),
    get_qty          integer CHECK (get_qty > 0),
    min_order_amount numeric(14, 2) CHECK (min_order_amount >= 0),
    scope            varchar(10)    NOT NULL DEFAULT 'ALL' CHECK (scope IN ('ALL', 'CATEGORY', 'PRODUCT')),
    category         varchar(100),
    product_id       uuid,
    coupon_code      varchar(40),
    starts_on        date,
    ends_on          date,
    active           boolean        NOT NULL DEFAULT true,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now(),
    version          bigint         NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id),
    CONSTRAINT promotions_percent_max CHECK (type NOT IN ('PERCENT_OFF', 'ORDER_PERCENT_OFF') OR value <= 100),
    CONSTRAINT promotions_bxgy CHECK (type <> 'BUY_X_GET_Y' OR (buy_qty IS NOT NULL AND get_qty IS NOT NULL)),
    CONSTRAINT promotions_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);
-- Coupon codes are unique per shop, ignoring case (SUMMER10 = summer10). Partial index: only rows with a code.
CREATE UNIQUE INDEX promotions_coupon_uk ON promotions (tenant_id, lower(coupon_code)) WHERE coupon_code IS NOT NULL;
SELECT enable_tenant_rls('promotions');

-- What was applied on each sale. line_discount (existing) now = promotion discount + manual discount.
ALTER TABLE sales_orders ADD COLUMN coupon_code varchar(40);
ALTER TABLE sales_order_lines
    ADD COLUMN promo_discount numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN promotion_name varchar(150);

INSERT INTO plan_modules (plan_code, module) VALUES ('BUSINESS', 'PROMOTIONS'), ('PRO', 'PROMOTIONS');
