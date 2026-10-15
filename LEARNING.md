# Learning guide: how Retail ERP works

This project was built in one go, so this file is the "explain after" part: a walk through every layer, in the
order of the original roadmap, with the Spring "magic" spelled out. Each section points to the files to read.

Suggested way to use it: read a section, open the files it mentions, then run the matching test and step through
it in the debugger. The tests are the best documentation of what the system does.

---

## 0. The big picture: one request, end to end

What happens when a cashier confirms a sale (`POST /api/sales-orders/{id}/confirm`):

```
Browser (React)                       fetch() with "Authorization: Bearer <JWT>"
  │
  ▼
Tomcat (embedded, started by Spring Boot)  ── one thread handles this request from start to finish
  │
  ▼
Spring Security filter chain
  ├─ BearerTokenAuthenticationFilter  verifies the JWT signature + expiry → SecurityContext = the user + roles
  └─ TenantContextFilter (ours)       copies the token's tenant_id claim into a ThreadLocal (TenantContext)
  │
  ▼
DispatcherServlet → SalesOrderController.confirm()
  ├─ @PreAuthorize checked by a proxy around the controller (method security)
  ▼
SalesService.confirm()   ← really a proxy: @Transactional opens a DB transaction here
  ├─ borrows a connection from HikariCP → our TenantAwareDataSource runs set_config('app.tenant_id', ...)
  ├─ Hibernate session opens → asks TenantIdentifierResolver for the tenant → adds tenant_id to every query
  ├─ StockService.lock() → pg_advisory_xact_lock per (product, warehouse)
  ├─ check levels → INSERT stock_movements (SALE) → UPDATE sales_orders → INSERT invoices
  └─ commit (or roll back everything on any exception)
  │
  ▼
Jackson turns the returned record into JSON → response
TenantContextFilter's finally block clears the ThreadLocal (the thread goes back to Tomcat's pool)
```

Keep this picture in mind; every section below zooms into one box.

---

## 1. Project setup (recap)

- `@SpringBootApplication` = `@Configuration` + `@ComponentScan` (finds our classes under `com.retailerp`) +
  `@EnableAutoConfiguration` (configures libraries it finds on the classpath).
- **Auto-configuration** is the core of Spring Boot's magic: e.g. "a JDBC driver and `spring.datasource.url` are
  present, and no `DataSource` bean was defined → create a HikariCP `DataSource`". Each auto-configuration is a
  normal `@Configuration` class guarded by `@Conditional...` annotations, so **defining your own bean makes the
  default back off**. We use this several times (`SecurityFilterChain`, `JwtDecoder`).
- To see what was auto-configured and why: run with `--debug` (prints the "conditions evaluation report").

---

## 2. Database: Docker Compose, DataSource, JPA, Flyway

### Docker Compose: [`compose.yaml`](compose.yaml)

- `postgres:17` pinned (never `latest`), a **named volume** (`postgres-data`) so data survives
  `docker compose down`, and a **healthcheck** (`pg_isready`) so `docker compose ps` says when it's actually ready.
- `${VAR:-default}`: values come from `.env` if present, else the dev defaults. `$${POSTGRES_USER}` (double `$`)
  is passed literally to the container's shell instead of being interpolated by Compose.
- The `POSTGRES_*` variables are only used when the volume is **empty** (first start). Changing the password later
  does nothing until you `docker compose down -v` (which **deletes the data**).

### How Spring connects: `DataSource` + HikariCP

`spring.datasource.*` in [`application.properties`](backend/src/main/resources/application.properties) is read by
`DataSourceAutoConfiguration`, which builds a **HikariCP connection pool**. Opening a PostgreSQL connection is slow
(TCP + auth), so the pool keeps ~10 open and lends them out. "Getting a connection" in this app means "borrowing
one from the pool"; "closing" it returns it.

### Two database roles (important for multi-tenancy)

| Role | Used by | Why |
|---|---|---|
| `erp_owner` (the compose superuser) | Flyway only (`spring.flyway.user`) | Creates tables, so it **owns** them |
| `erp_app` (created by migration V1) | The running app (`spring.datasource.username`) | Not owner, not superuser → **RLS applies to it** |

Postgres skips Row-Level Security for table owners and superusers. If the app connected as the owner, every RLS
policy would be silently ignored. This is the most common RLS mistake.

### Flyway: [`db/migration`](backend/src/main/resources/db/migration)

- At startup, `FlywayAutoConfiguration` runs any `V<n>__*.sql` not yet listed in the `flyway_schema_history`
  table, in order, each in a transaction. Spring Boot makes Hibernate wait for Flyway.
- **Never edit a migration that has already run.** Flyway stores a checksum and refuses to start. Fix forward
  with a new `V7__...sql`.
- `${app_db_user}` in the SQL files are **Flyway placeholders**, filled from `spring.flyway.placeholders.*`, so
  the app password isn't hard-coded in a migration.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate **checks** that the entities match the tables and fails at
  startup if not, but never changes the schema. (`update` is tempting and dangerous: it can't rename, never drops,
  and guesses types.)

### JPA, Hibernate, entities

JPA is the standard API (`jakarta.persistence.*`); Hibernate 7 is the implementation. An **entity** is a class
mapped to a table. Read [`TenantScopedEntity`](backend/src/main/java/com/retailerp/common/TenantScopedEntity.java),
[`AuditedEntity`](backend/src/main/java/com/retailerp/common/AuditedEntity.java),
[`Product`](backend/src/main/java/com/retailerp/catalog/Product.java) and
[`PurchaseOrder`](backend/src/main/java/com/retailerp/purchasing/PurchaseOrder.java):

- `@MappedSuperclass`: shared columns (id, tenant_id, timestamps) without a table of its own.
- `@GeneratedValue(strategy = UUID)`: Hibernate creates the id in Java, so it exists before the INSERT.
- `@Version`: **optimistic locking**. Every UPDATE includes `WHERE version = ?`. If someone else saved in
  between, zero rows match and Hibernate throws → we answer 409 "Reload and try again".
- **Dirty checking:** an entity loaded inside a transaction is *managed*. Change its fields and Hibernate writes
  the UPDATE at commit; there's no `save()` call in `updateProduct`. That surprises everyone at first.
- `FetchType.LAZY` on `@ManyToOne`: the related row is loaded only when accessed. Accessing it **after** the
  transaction ends throws `LazyInitializationException`; that's why services return DTOs, not entities.
- **N+1 queries:** listing 20 orders and touching `order.getSupplier()` on each = 1 + 20 queries.
  `@EntityGraph(attributePaths = {"supplier"})` on the repository method fetches them in one JOIN.
  `hibernate.default_batch_fetch_size=50` is a safety net for the rest.
- `cascade = ALL, orphanRemoval = true` on order lines: lines are saved/deleted through their order. The
  order is the **aggregate root**, and lines are never touched directly.
- Domain methods (`markReceived()`, `confirm()`, `registerPayment()`) enforce state transitions inside the entity,
  so an invalid transition is impossible from anywhere in the code.

### Spring Data repositories

`interface ProductRepository extends JpaRepository<Product, UUID>` has **no implementation in our code**. At
startup Spring Data creates a proxy class implementing it:

- CRUD from `JpaRepository` (`findById`, `save`, `delete`, paging...).
- **Derived queries** from method names: `countByActiveTrue()` → `select count(*) ... where active = true`.
- `@Query` with JPQL (queries on *entities and fields*, not tables and columns). JPQL can build DTOs directly:
  `select new com.retailerp.inventory.StockDtos$StockLevel(...)`.
- **Specifications** ([`StockService#movements`](backend/src/main/java/com/retailerp/inventory/StockService.java)):
  build the WHERE clause at runtime from optional filters.
- `Pageable` parameters in controllers are filled by Spring MVC from `?page=0&size=20&sort=name,asc`.

---

## 3. REST layer: controllers, DTOs, validation, errors

- `DispatcherServlet` (auto-configured) receives every request and routes it to the `@RestController` method
  whose `@GetMapping`/`@PostMapping` matches. Return values are written as JSON by **Jackson 3**
  (package `tools.jackson`, a Boot 4 change; annotations are still `com.fasterxml.jackson.annotation`).
- **DTOs as Java records** (`*Dtos.java`): the API shape is separate from the entities. This avoids leaking
  fields (password hashes!), avoids lazy-loading surprises during serialization, and lets the database evolve
  without breaking clients.
- **Validation:** `@Valid @RequestBody` triggers Bean Validation (`@NotBlank`, `@Email`, `@DecimalMin`...).
  `List<@Valid LineRequest>` validates each line too.
- **Errors:** [`GlobalExceptionHandler`](backend/src/main/java/com/retailerp/common/GlobalExceptionHandler.java) is
  a `@RestControllerAdvice` that turns exceptions into **RFC 9457 problem details**:
  `{"type","title","status","detail"}` with `Content-Type: application/problem+json`. Domain errors use
  `BusinessRuleException` → 409; missing records → 404; validation → 400 with an `errors` map per field.
- **Actions as sub-resources:** `POST /purchase-orders/{id}/receive` instead of `PUT {status: "RECEIVED"}`.
  Receiving has side effects (stock movements), so it's an action, not a field edit.
- Swagger UI: http://localhost:8080/swagger-ui.html (generated by springdoc from the controllers; click
  "Authorize" and paste a token from `/api/auth/login`).

---

## 4. Transactions

`@Transactional` on a service class means: Spring wraps the bean in a **proxy**; each public method call goes
through the proxy, which begins a transaction, calls the real method, and commits (or rolls back on a
`RuntimeException`).

Things to know:

- **Self-invocation doesn't go through the proxy.** `this.otherMethod()` inside the same class ignores
  `otherMethod`'s `@Transactional` settings. Put it on another bean if it matters.
- `readOnly = true`: a hint (Hibernate skips dirty checking; the driver can optimize).
- `Propagation.MANDATORY` on `StockService.addStock/removeStock/lock` and `DocumentNumberService.next`:
  "must be called inside an existing transaction". Stock changes and the document that causes them must commit
  together, so these methods refuse to run on their own.
- `spring.jpa.open-in-view=false`: by default Boot keeps the Hibernate session (and a DB connection) open for the
  entire HTTP request so lazy loading works in controllers. That hides N+1 problems and holds connections longer;
  we turn it off and map to DTOs inside the transaction.
- `JdbcTemplate` and JPA share the same connection inside a transaction (the `JpaTransactionManager` exposes it),
  which is why the advisory lock and the document-number upsert are part of the same transaction as the entity
  changes.

---

## 5. Security and JWT

Read [`SecurityConfig`](backend/src/main/java/com/retailerp/security/SecurityConfig.java),
[`JwtService`](backend/src/main/java/com/retailerp/security/JwtService.java),
[`AuthService`](backend/src/main/java/com/retailerp/security/AuthService.java).

- Adding Spring Security puts a **filter chain** in front of the `DispatcherServlet`. By default Boot locks every
  URL behind HTTP Basic with a generated password. Declaring our own `SecurityFilterChain` bean replaces that.
- **Stateless:** no HTTP session and no cookie. Every request carries `Authorization: Bearer <token>`.
- **JWT** = `base64url(header).base64url(claims).signature`. Our claims: `sub` (user id), `tenant_id`, `roles`,
  `email`, `name`, `iss`, `iat`, `exp`. Anyone can **read** them (paste a token into jwt.io); the HMAC-SHA256
  signature only proves we issued it unchanged. Never put secrets in claims.
- We use Spring Security's **OAuth2 resource server** support (`spring-boot-starter-security-oauth2-resource-server`)
  with Nimbus for signing/verifying, instead of a third-party JWT library. It validates signature, expiry and issuer.
- **Roles:** the `roles` claim becomes authorities `ROLE_ADMIN`... via `JwtGrantedAuthoritiesConverter`, so
  `hasRole('ADMIN')` works. `@EnableMethodSecurity` turns on `@PreAuthorize`; the expressions live in
  [`Access`](backend/src/main/java/com/retailerp/security/Access.java).
- **CSRF disabled:** CSRF attacks work because browsers attach cookies automatically. A bearer token in a header
  is never attached automatically, so CSRF doesn't apply. (If you ever move the token to a cookie, re-enable it.)
- **CORS:** the browser blocks calls from `localhost:5173` to `localhost:8080` unless the server allows that
  origin. In development, Vite's proxy makes everything same-origin anyway.
- **Passwords:** BCrypt via `DelegatingPasswordEncoder` (stored as `{bcrypt}$2a$...`, so the algorithm can be
  upgraded later). Login compares against a dummy hash when the email doesn't exist, so response timing doesn't
  reveal which emails are registered.
- **Trade-off to know:** a JWT stays valid until it expires (8h here). Disabling a user doesn't kill their current
  token. The production answer is short-lived access tokens + refresh tokens (see "Next steps").

---

## 6. Multi-tenancy (the most important part)

Every tenant-owned table has `tenant_id`. Isolation is enforced **three times**:

1. **Hibernate `@TenantId`** on [`TenantScopedEntity`](backend/src/main/java/com/retailerp/common/TenantScopedEntity.java).
   When a session opens, Hibernate asks
   [`TenantIdentifierResolver`](backend/src/main/java/com/retailerp/tenancy/TenantIdentifierResolver.java) for
   the current tenant. It then sets `tenant_id` on every insert and adds `tenant_id = ?` to every query,
   including `findById`. That's why tenant B gets a **404** for tenant A's product: the row is simply not
   found.
2. **PostgreSQL Row-Level Security** (migration V1 `enable_tenant_rls`). Each table gets
   `USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())`. `USING` filters what
   you can read/update/delete; `WITH CHECK` validates what you write. `current_tenant_id()` reads the session
   setting `app.tenant_id`, which
   [`TenantAwareDataSource`](backend/src/main/java/com/retailerp/tenancy/TenantAwareDataSource.java) sets on
   **every** connection borrowed from the pool. This protects us from native SQL, `JdbcTemplate`, and bugs
   where someone forgets the filter.
3. **Composite foreign keys** `(tenant_id, product_id) → products(tenant_id, id)`: the database refuses a row
   that points to another tenant's row.

Where the tenant comes from: **only the signed JWT** (`TenantContextFilter`). Never from a header, URL or request
body, which the client controls.

When there is no tenant (unauthenticated requests): the resolver returns a "nil" UUID and the connection setting
is empty → `current_tenant_id()` is NULL → every policy matches zero rows. It **fails closed**.

Two deliberate holes, both narrow:

- **Login** must find a user before knowing their tenant. `auth_find_user_by_email()` is a `SECURITY DEFINER`
  function (runs with the owner's rights, so it bypasses RLS) that returns exactly one row by email.
- **Registration** generates the new tenant id first and sets it as current *before* the transaction starts, so the
  inserts satisfy `WITH CHECK`.

Gotchas solved here:

- ThreadLocals must be cleared (`finally`) or the next request on that pooled thread inherits the tenant.
- The `set_config` runs while the connection is still in auto-commit mode, so the value is committed and lasts for
  the whole borrow; with `is_local = true` it would vanish at the first commit.
- Proof: [`TenantIsolationTest`](backend/src/test/java/com/retailerp/TenantIsolationTest.java) checks isolation
  through the API **and** with raw SQL (Hibernate bypassed, so only RLS protects), plus a cross-tenant INSERT
  rejected by RLS.

---

## 7. Inventory: stock is never edited

- There is no `quantity` column on products. [`StockMovement`](backend/src/main/java/com/retailerp/inventory/StockMovement.java)
  rows are appended (`+10 PURCHASE_RECEIPT`, `-3 SALE`, `+3 SALE_RETURN`...), and the level is `SUM(quantity)`.
  Benefits: a full audit trail, any past level can be reconstructed, and no lost updates from two people editing
  one number.
- **Append-only, enforced three ways:** `@Immutable` (Hibernate never UPDATEs), no setters, and in the database the
  app role has no UPDATE/DELETE privilege + a trigger rejects them for everyone. Mistakes are fixed with a
  correcting movement (an ADJUSTMENT), like accounting.
- **Concurrency:** "check stock, then insert" is a race. Two transactions could both see 5 in stock and both sell
  5. [`StockService.lock`](backend/src/main/java/com/retailerp/inventory/StockService.java) takes
  `pg_advisory_xact_lock(hash(product, warehouse))`, held until commit, so the check+insert for one pair runs
  one at a time. Locks are taken in **sorted order** so two orders with the same products can't deadlock.
  `RetailFlowTest.concurrentSalesNeverOversell` fires 6 parallel confirmations at 5 units: exactly one wins.
- Stock *increases* don't need the lock (they can't make stock negative).
- Performance note: `SUM` over all movements is fine for thousands of rows per product. At large scale you'd add
  a balance/snapshot table maintained in the same transaction. Movements stay the source of truth.

---

## 8. Purchasing, sales, invoicing

- **Purchase order:** `DRAFT` (editable) → `ORDERED` → `RECEIVED` (one `PURCHASE_RECEIPT` movement per line; the
  line cost becomes the product's cost price). `DRAFT/ORDERED` → `CANCELLED`.
- **Sales order:** `DRAFT` → `CONFIRMED` = lock + check + `SALE` movements + invoice, **all in one transaction**
  (if anything fails, nothing happened). Cancelling a confirmed sale (managers only) voids the invoice (refused if
  it has payments) and adds `SALE_RETURN` movements. The original movements are never deleted.
- **Prices are copied** onto lines and invoices. Changing a product's price later must not change old invoices.
- **Money** is `BigDecimal` with 2 decimals, half-up rounding ([`Money`](backend/src/main/java/com/retailerp/common/Money.java)).
  Never `double`: `0.1 + 0.2 != 0.3`.
- **Payments** lock the invoice row (`@Lock(PESSIMISTIC_WRITE)` = `SELECT ... FOR UPDATE`) so two simultaneous
  payments can't both pass the "not more than the balance" check.
- **Document numbers** (`PO-000001`) come from a per-tenant counter row updated with
  `INSERT ... ON CONFLICT DO UPDATE ... RETURNING`. The row lock serializes concurrent orders, and a rollback
  gives the number back.

---

## 8b. Plans, add-ons and the platform admin

Package [`subscription`](backend/src/main/java/com/retailerp/subscription) and [`platform`](backend/src/main/java/com/retailerp/platform),
migration `V7__plans_and_subscriptions.sql`.

- **Module** = a sellable feature area (enum `Module`). **Plan** = modules + limits (`plans`, `plan_modules` tables).
  **Add-on** = one module bought on top of a plan (`tenant_addons`). **Entitlements** = what the tenant may use
  *right now*, computed per request: effective plan (the trial plan while the trial runs) + add-ons.
- **Gating an API:** `@RequiresModule(Module.PURCHASING)` on a controller. A Spring MVC `HandlerInterceptor`
  ([`ModuleAccessInterceptor`](backend/src/main/java/com/retailerp/subscription/ModuleAccessInterceptor.java)) runs
  before every controller method, reads the annotation and checks the entitlements → 403 with
  `"code": "MODULE_NOT_IN_PLAN"`. Interceptors run *after* the security filters (so the tenant is known) and
  *before* the controller. Filters wrap the whole servlet; interceptors wrap only Spring MVC handlers.
- **Limits:** checked in the services before creating (or re-enabling) a user or warehouse → 409
  `PLAN_LIMIT_REACHED`. Only *active* records count, so deactivating one frees a slot.
- **A shop can't upgrade itself, even through a bug:** the app's DB role can read its subscription but has no
  UPDATE privilege on it, and no INSERT on add-ons. Plan changes go through `platform_set_subscription()`, a
  SECURITY DEFINER function only called from endpoints restricted to `PLATFORM_ADMIN`.
- **Platform admins** are not tenant users: separate table, separate login (`/api/platform/auth/login`), a JWT with
  role `PLATFORM_ADMIN` and **no** `tenant_id`. Tenant endpoints require a tenant role (`Access.ANY`), so a
  platform token can't use them, and a shop token can't use `/api/platform/**`.
- **Bootstrap:** the first platform admin is created at startup from `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD`
  by an `ApplicationRunner` (code Spring runs once after startup). No password in git.
- **Frontend:** `GET /api/subscription` drives everything (menu items hidden per module, trial banner, plan page,
  limits on the Users/Warehouses pages). That's convenience only; the backend enforces it.
- **Adding a new module later:** add the enum value, a migration inserting it into `plan_modules` for the right
  plans, `@RequiresModule` on its controllers, and the `module` field on its menu entries.

---

## 8c. Point of Sale

Package [`pos`](backend/src/main/java/com/retailerp/pos), migration `V8__point_of_sale.sql`, screen
[`PosPage.tsx`](frontend/src/pages/PosPage.tsx).

- **POS reuses the existing modules.** A checkout = create a sales order → confirm it (stock lock + SALE movements +
  invoice) → record the payment, **all in one transaction** (`PosService.checkout`). If the cash is short or the
  stock is missing, the exception rolls back everything: no half-made sale.
  `SalesService.createConfirmed` uses `Propagation.MANDATORY` so it can only run inside that transaction.
- **Shifts:** a cashier opens a shift with the cash in the drawer, sells, then closes it with a cash count.
  Expected cash = opening float + cash payments during the shift (a JPQL query that walks
  `Payment → Invoice → SalesOrder.posShiftId`). The difference is stored on the shift.
  One open shift per cashier is enforced by a **partial unique index** (`WHERE status = 'OPEN'`): a unique
  constraint that only applies to some rows.
- **Discounts:** an order-level % applied to every line (`line_discount`), with tax computed on the discounted amount:
  `subtotal − discount + tax = total`. Cashiers are limited to 10% (`app.pos.cashier-max-discount-percent`), whether
  they lower a price or set a %; managers aren't. The limit is checked in `SalesService`, so it also applies to
  normal sales orders.
- **No package cycle:** `SalesOrder` stores `pos_shift_id` as a plain UUID instead of a `@ManyToOne PosShift`, so
  the sales module doesn't depend on the POS module (POS depends on sales, not the reverse).
- **Receipt printing:** the receipt is normal HTML; printing adds a CSS class to `<body>` so the print stylesheet
  hides everything except the receipt, sized for a till printer (72 mm).
- **Gotcha we hit:** a CSS class named `pos` (for the layout) clashed with an existing `.pos` (green "positive
  number" text). Global CSS class names collide; CSS Modules or a naming convention avoid it.

---

## 8d. Reports

Package [`reports`](backend/src/main/java/com/retailerp/reports), migration `V9__reports.sql`, page
[`ReportsPage.tsx`](frontend/src/pages/ReportsPage.tsx).

- **Plain SQL for reporting.** `GROUP BY`, `date_trunc('week', order_date)` and `SUM` are what SQL does best, and
  nothing needs to be an entity, so `ReportService` uses `JdbcTemplate`. JPA is for changing data; SQL is fine
  (and faster) for reading aggregates.
- **No `WHERE tenant_id = ?` in those queries:** Row-Level Security filters every row to the current tenant. Here the
  "backup" layer does the main job, which is why `TenantIsolationTest` checks RLS with raw SQL.
- **Cost snapshot:** profit = net sales − cost of goods sold. If we used the product's *current* cost, changing a
  cost price would rewrite the profit of every past sale. So each sales line stores `unit_cost` when it's created
  (`ReportsIntegrationTest.profitUsesTheCostAtTheTimeOfSale`). Rows from before V9 were backfilled with the cost at
  migration time: a data migration (`UPDATE` in Flyway), followed by `SET NOT NULL` once every row has a value.
- **Partial index** `sales_orders_report_idx ... WHERE status = 'CONFIRMED'`: reports only read confirmed sales by
  date, so the index only contains those rows.
- **Stock value** = on-hand quantity × *current* cost price (a simple "standard cost" method; FIFO/average costing
  would need cost layers per receipt, a later improvement).
- **SQL injection note:** `date_trunc('%s', ...)` inserts text into SQL, which is normally dangerous. Here the value
  comes from a Java enum (`DAY/WEEK/MONTH`), never from user text; everything user-provided is a `?` parameter.
- **CSV export is done in the browser** ([`csv.ts`](frontend/src/components/csv.ts)): the data is already on screen.
  Values with commas/quotes/newlines are quoted, and a BOM (`\uFEFF`) makes Excel read accents correctly.
  (Excel in some locales expects `;` instead of `,`; if that bites, the separator can become a setting.)
- **Print/PDF:** `@media print` hides the menu, tabs and buttons and shows a header with the shop and period;
  the browser's "Save as PDF" does the rest.

---

## 9. Frontend

- **Vite** serves the React app in dev with instant reload and builds static files for production. Its proxy
  forwards `/api` to Spring Boot, so the browser only talks to one origin.
- **TanStack Query** caches server data by key (`['products', search, page]`). After a change we
  `invalidateQueries`, and every screen showing that data refetches. That replaces most hand-written
  `useEffect` + `useState` loading code.
- **Auth:** [`AuthContext`](frontend/src/auth/AuthContext.tsx) holds the user; the token is in `localStorage`;
  any 401 logs out; on logout the query cache is cleared so the next user never sees the previous tenant's data.
  Trade-off: `localStorage` is readable by any script on the page (XSS). The stronger alternative is an
  `HttpOnly` cookie (then CSRF protection comes back).
- **Role checks in the UI are only for convenience.** The backend enforces everything; hiding a button isn't
  security.

---

## 10. Testing

- Integration tests use a **real PostgreSQL**, because RLS, advisory locks, `ON CONFLICT` and triggers don't exist
  in H2. [`IntegrationTest`](backend/src/test/java/com/retailerp/IntegrationTest.java) starts `postgres:17` with
  **Testcontainers** (needs Docker), or uses `TEST_DB_URL` if set.
- Tests talk to the app through **MockMvc** (full Spring MVC + security, without a real HTTP port).
- Each test registers fresh tenants with random emails, so tests never interfere and nothing needs cleaning up.
  It's also a free extra isolation check.
- The Spring context is cached across test classes with the same configuration, so it starts only once.

---

## 11. Common mistakes (and where this codebase avoids them)

| Mistake | Here |
|---|---|
| App connects to Postgres as the table owner → RLS silently off | Separate `erp_app` role |
| Tenant id taken from a request header | Only from the signed JWT |
| ThreadLocal not cleared | `finally` in `TenantContextFilter`, `TenantContext.callAs` |
| `ddl-auto=update` in real use | Flyway + `validate` |
| Editing an applied migration | Fix forward with a new version |
| Returning entities from controllers | Record DTOs |
| N+1 queries | `@EntityGraph`, batch fetch size, OSIV off |
| Check-then-act race on stock | Advisory locks per product/warehouse |
| `double` for money | `BigDecimal` + `Money` helper |
| Editing stock in place | Append-only movements |
| Secrets in git | `.env` git-ignored, `JWT_SECRET` from the environment |
| Catch-all exception handler swallowing 403s | Explicit `AccessDeniedException` handler |

---

## 12. Next steps (not built yet)

- Refresh tokens + short access tokens, logout/revocation; rate limiting on `/api/auth/login`.
- Email verification and password reset.
- A stock balance table for very large movement volumes; stock counts (cycle counting) workflow.
- Partial receipts on purchase orders; returns/refunds as their own documents; credit notes.
- Audit log (who changed what), soft deletes where needed.
- Dockerfile for the backend, serving the built frontend, HTTPS, production profile (`springdoc` off).
- Frontend tests (Vitest + Testing Library, Playwright end-to-end).
