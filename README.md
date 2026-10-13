# Retail ERP

A multi-tenant ERP for retail businesses: products, warehouses, stock, purchasing, sales, invoicing.

- **Backend:** Spring Boot 4.1 · Java 21 · PostgreSQL 17 · Flyway · Spring Security + JWT
- **Multi-tenancy:** shared tables with `tenant_id`, Hibernate `@TenantId`, plus PostgreSQL Row-Level Security
- **Frontend:** React 19 · TypeScript · Vite · TanStack Query

New here? Read [`LEARNING.md`](LEARNING.md) for a guided tour of how everything works, and [`CONTEXT.md`](CONTEXT.md)
for the project's decisions and status.

## Run it locally

Prerequisites: Docker Desktop, JDK 21, Node.js 22+.

```bash
# 1. Database (from the repo root)
docker compose up -d                 # PostgreSQL 17 on localhost:5432
docker compose ps                    # wait for "healthy"

# 2. Backend (from backend/)
./mvnw spring-boot:run               # Windows: .\mvnw.cmd spring-boot:run (set JAVA_HOME to JDK 21 first)
#    → http://localhost:8080/actuator/health  and  http://localhost:8080/swagger-ui.html

# 3. Frontend (from frontend/)
npm install
npm run dev                          # → http://localhost:5173, click "Create an account"
```

Flyway creates the schema on the first start. The defaults in `compose.yaml` and `application.properties` match
each other, so no configuration is needed for local development. To change them, copy `.env.example` to `.env`
(used by Docker Compose) and set the same variables in your shell for the backend.

For anything beyond local development, set `JWT_SECRET` (`openssl rand -base64 32`). Without it, the backend
generates a random key at startup and every token becomes invalid on restart.

## Plans and the platform admin

Shops get modules according to their plan (Starter / Business / Pro), plus optional add-on modules. New shops
start with a 14-day Pro trial. The people running the platform assign plans in the **platform admin panel**:

```bash
# Set these before starting the backend (the account is created at startup; password ≥ 12 characters)
PLATFORM_ADMIN_EMAIL=you@example.com PLATFORM_ADMIN_PASSWORD='a-long-password' ./mvnw spring-boot:run
# PowerShell: $env:PLATFORM_ADMIN_EMAIL="you@example.com"; $env:PLATFORM_ADMIN_PASSWORD="a-long-password"
```

Then open http://localhost:5173/platform/login.

## Tests

```bash
cd backend && ./mvnw test            # needs Docker: Testcontainers starts a throwaway postgres:17
```

Or point the tests at an existing PostgreSQL server (the user needs rights to create roles):

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5432/retail_erp_test TEST_DB_ADMIN_USER=postgres TEST_DB_ADMIN_PASSWORD=... ./mvnw test
```

Frontend type check and production build: `cd frontend && npm run build`.

## Roles

| Role | Can do |
|---|---|
| ADMIN | Everything, including managing users |
| MANAGER | Everything except users: catalog, purchasing, stock adjustments, cancelling confirmed sales |
| CASHIER | Sales orders, payments, customers; read-only products, stock and warehouses |

## Repository layout

```
compose.yaml      PostgreSQL for local development
backend/          Spring Boot application (package com.retailerp, one sub-package per module)
frontend/         React application
.github/          CI: backend tests + frontend build
```
