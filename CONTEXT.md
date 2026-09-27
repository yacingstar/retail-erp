# Retail ERP: Project Context

> **Source of truth for this project.** Read this first at the start of every session (human or AI).
> Keep it up to date: when a decision is made, a step is finished, or a new gotcha appears, update the relevant section.

_Last updated: 2026-09-27_

---

## 1. Current status (update this first)

| | |
|---|---|
| **Current phase** | Step 1 (project setup) ✅ done → **Step 2 (Entities, JPA, Flyway) next** |
| **Last thing done** | Fixed the Spring Boot parent version, built and ran the skeleton (`/actuator/health` → UP), `git init`, added `CONTEXT.md` + `CLAUDE.md`, pushed `main` to GitHub (`origin`, HTTPS) |
| **Pending / open** | Nothing |
| **Next step** | PostgreSQL via Docker Compose + connect Spring to it (explain how `DataSource` auto-configuration works, then Flyway). Plan: the **user writes `docker-compose.yml`**, Claude reviews it. |

---

## 2. What we're building

A **multi-tenant ERP for retail businesses**: inventory, sales, purchasing, invoicing.

- **Backend:** Spring Boot, Java 21, Maven, PostgreSQL (Docker Compose), Flyway, Spring Security + JWT
- **Multi-tenancy:** shared tables with a `tenant_id` column, Hibernate `@TenantId`, and **Postgres Row-Level Security (RLS) as a backup** layer
- **Frontend (later):** React, in `retail-erp/frontend/`
- **Core domain rule:** **stock is never edited directly.** Every stock change is a **stock movement** (an append-only record); the current stock level is derived from the movements.

---

## 3. How the user wants to work (IMPORTANT for any AI assistant)

The user is here to **LEARN**, not just to get code.

- Work in **small steps**: one concept or one feature at a time. **Never generate a whole module at once.**
- **Before writing code**, explain what we're about to do and **WHY** it's done that way.
- **Explain Spring "magic"** when it happens: auto-configuration, the embedded server, dependency injection, annotations. The user's past Spring experience was application-level only; they never learned what happens underneath.
- **Before running any terminal command**, explain what it does in plain words.
- When there are **several ways** to do something, present the options and trade-offs, then **let the user choose**.
- **Sometimes let the user write the code**: describe what's needed, they write it, Claude reviews it.
- **After each step, stop and wait** for the user before moving on. Say **how to test** what was just built.
- Point out **common mistakes and bad practices** related to what we're doing.
- Things that need the user at the keyboard (logins, generating keys, passphrases) are run by the user, not by the assistant.

---

## 4. Order of work (roadmap)

1. ✅ Project setup and structure
2. ⏭️ Entities, JPA, Flyway migrations
3. REST controllers, DTOs, validation, error handling
4. Spring Security + JWT
5. Multi-tenancy + isolation tests
6. Products + inventory module
7. Frontend, then alternate backend/frontend per module

---

## 5. Decisions made (and why)

| Decision | Choice | Reason |
|---|---|---|
| Spring Boot version | **4.1.1** (not 3.x as the original plan said) | New project, so no migration later; the core concepts are the same as 3.x. Where Boot 4 differs from online tutorials (see §7), the assistant points it out. |
| Git repository location | **`retail-erp/` root** (monorepo: backend + future frontend) | One repo, so API and UI changes can land in one commit |
| Default branch | `main` | |
| Remote | `origin` = `https://github.com/yacingstar/retail-erp.git` (**HTTPS**) | The user has no SSH key set up; HTTPS + Git Credential Manager (`credential.helper=manager`) is simpler. SSH can be set up later. |
| `JAVA_HOME` | Set **per terminal** only (system default left at JDK 17) | Don't break the user's other JDK 17 projects |
| Maven | Always use the wrapper `./mvnw` (or `.\mvnw.cmd`), never a global `mvn` | Everyone builds with the same Maven version (3.9.16) |
| Security starter | **Not added yet**, comes in step 4 | Adding it locks all endpoints (401s) and gets in the way of learning steps 2–3 |

---

## 6. Repository layout

```
retail-erp/                      ← git root
├── CONTEXT.md                   ← this file
├── CLAUDE.md                    ← just "@CONTEXT.md": makes Claude Code auto-load this file every session
└── backend/                     ← Spring Boot app (Maven)
    ├── pom.xml
    ├── mvnw, mvnw.cmd, .mvn/    ← Maven Wrapper (Maven 3.9.16)
    ├── .gitattributes           ← mvnw forced to LF, *.cmd to CRLF
    ├── .gitignore               ← ignores target/, .vscode/, .idea/, HELP.md, ...
    └── src/
        ├── main/java/com/retailerp/ErpBackendApplication.java
        ├── main/resources/application.properties   (only spring.application.name=erp-backend)
        └── test/java/com/retailerp/ErpBackendApplicationTests.java  (contextLoads)
```

- **Maven coordinates:** `com.retailerp:erp-backend:0.0.1-SNAPSHOT`
- **Base package:** `com.retailerp`. **All code must live under it**, or component scanning won't find it.
- **Current dependencies:** `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator` (+ their `-test` starters).
- **Not yet added:** Spring Data JPA, PostgreSQL driver, Flyway, Spring Security, JWT library.

---

## 7. Spring Boot 4 notes (differences vs. most tutorials, which use Boot 3)

- Starters are **more modular**: `spring-boot-starter-webmvc` (not `starter-web`), plus separate **`-test` starters** per feature (e.g. `spring-boot-starter-webmvc-test`).
- Built on **Spring Framework 7**, **Jakarta EE 11** (Tomcat 11: the app ran on Apache Tomcat 11.0.24), **Hibernate 7**, **Jackson 3** (some package names changed).
- Version numbers have **no `.RELEASE` suffix** (it's `4.1.1`, not `4.1.1.RELEASE`).
- Actuator responses use the content type `application/vnd.spring-boot.actuator.v3+json`.

---

## 8. Development environment

- **OS:** Windows 10 Pro. Shells available: PowerShell 5.1 and Git Bash.
- **IDE:** VS Code with the Java extension (`backend/.vscode/settings.json` has `java.configuration.updateBuildConfiguration: automatic`; `.vscode/` is gitignored).
- **JDKs installed:**
  - `C:\Program Files\Java\jdk-17` ← **system `JAVA_HOME` points here (wrong for this project)**
  - `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` ← **use this one**
  - `C:\Program Files\Java\jdk-24`
- **Docker:** 28.3.2 (installed; not used yet)
- **Git:** 2.53 (Windows), identity `Ahmed Yacine <67834851+yacingstar@users.noreply.github.com>`, `credential.helper=manager`
- **GitHub CLI (`gh`):** not installed
- **SSH keys:** none (`~/.ssh` only has `known_hosts` with github.com)

### Commands

PowerShell, from `D:\CODE\retail-erp\backend`:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"   # every new terminal
.\mvnw.cmd spring-boot:run        # run the app (http://localhost:8080)
.\mvnw.cmd test                   # run tests
.\mvnw.cmd clean package          # build the fat jar → target/erp-backend-0.0.1-SNAPSHOT.jar
java -jar target\erp-backend-0.0.1-SNAPSHOT.jar
```

Git Bash equivalent: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"` then `./mvnw ...`.

Health check: `http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

## 9. Gotchas already hit (don't repeat them)

1. **Wrong parent version from the generator:** `pom.xml` had `4.1.1.RELEASE`, which doesn't exist on Maven Central. Fixed to `4.1.1`. Maven **caches "not found" failures**, so after fixing it we had to run with `-U` (force update) once.
2. **Java version mismatch:** the pom requires Java 21, but the system `JAVA_HOME` is JDK 17, which gives "release version 21 not supported". Set `JAVA_HOME` to JDK 21 first.
3. **VS Code Java extension vs. Maven race:** when `pom.xml` changes, VS Code re-imports and rebuilds `target/classes` **at the same time** as a command-line Maven build. The result was "Unable to find main class" and an empty 22-byte jar. Fix: let the IDE finish, then rerun (or use `clean package`).
4. **Line endings:** Git on Windows warns "LF will be replaced by CRLF". Harmless. `mvnw` must stay **LF** (guaranteed by `.gitattributes`), or it breaks on Linux/CI. `mvnw` is also marked executable in git (`git update-index --chmod=+x`).
5. **GitHub repo creation:** create it **empty** (no README/.gitignore/license), or the first push is rejected because the histories are unrelated. Never "fix" that with `--force`.

---

## 10. Rules & conventions (grows as we go)

- **Never commit secrets** (DB passwords, JWT signing key), even though the repo may be private. Use environment variables / a non-committed local file.
- **Never commit `target/`** or IDE folders.
- Stock changes only through **stock movements**; no `UPDATE stock SET quantity = ...`.
- Every tenant-owned table has `tenant_id`; isolation is enforced by Hibernate `@TenantId` **and** Postgres RLS.
- Database schema changes only through **Flyway migrations** (never `ddl-auto=update` in real use).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` when Claude authored the commit.

---

## 11. Session log

### 2026-09-27: Session 1
- Found the Spring Initializr skeleton in `D:\CODE\retail-erp\backend` (never built, no git).
- Explained what Initializr generated: parent POM / dependency management, Maven Wrapper, `@SpringBootApplication` = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`, and the `contextLoads` test.
- Decided: Boot 4, git at the repo root, per-terminal `JAVA_HOME`.
- Fixed the parent version; built successfully (1 test passing, 23 MB fat jar); ran the jar: Tomcat auto-started on 8080, `/actuator/health` UP, unknown path → default JSON 404.
- `git init -b main` at `retail-erp/`, first commit `fa2bbb7`.
- The user created the GitHub repo `yacingstar/retail-erp`; switched the remote from SSH to HTTPS (no SSH key).
- Created this `CONTEXT.md` + `CLAUDE.md` (auto-load), committed, and pushed `main` to GitHub.
