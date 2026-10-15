package com.retailerp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for integration tests: the full Spring context + a REAL PostgreSQL (RLS can't be tested on H2).
 * <p>
 * By default a throwaway {@code postgres:17} container is started with Testcontainers (needs Docker).
 * Alternatively, point the tests at an existing server with TEST_DB_URL / TEST_DB_ADMIN_USER / TEST_DB_ADMIN_PASSWORD.
 * <p>
 * The container is started once (static) and shared by all test classes; Spring also caches the context.
 * Tests don't clean up: each one registers fresh tenants with unique emails, so they never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");
    private static final PostgreSQLContainer POSTGRES;

    static {
        if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
            POSTGRES = new PostgreSQLContainer("postgres:17");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.flyway.user", POSTGRES::getUsername);
            registry.add("spring.flyway.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.flyway.user", () -> env("TEST_DB_ADMIN_USER", "postgres"));
            registry.add("spring.flyway.password", () -> env("TEST_DB_ADMIN_PASSWORD", ""));
        }
        // The app itself connects as the RLS-restricted role, created by the first migration.
        registry.add("spring.datasource.username", () -> "erp_app");
        registry.add("spring.datasource.password", () -> "erp_app_test_password");
        registry.add("app.platform.admin-email", () -> PLATFORM_EMAIL);
        registry.add("app.platform.admin-password", () -> PLATFORM_PASSWORD);
    }

    protected static final String PLATFORM_EMAIL = "platform@example.com";
    protected static final String PLATFORM_PASSWORD = "platform-password-123";

    /** Logs in as the platform admin (created at startup from the properties above). */
    protected Session platformAdmin() throws Exception {
        String body = mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", PLATFORM_EMAIL, "password", PLATFORM_PASSWORD)))).andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.token"), null, null, PLATFORM_EMAIL);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    /** A registered tenant with its admin's token. */
    public record Session(String token, UUID tenantId, UUID userId, String email) {
    }

    protected Session registerTenant(String company) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("companyName", company, "fullName", "Admin of " + company, "email", email, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.token"), UUID.fromString(JsonPath.read(body, "$.user.tenantId")),
                UUID.fromString(JsonPath.read(body, "$.user.id")), email);
    }

    /** Logs in an existing user (e.g. one created via /api/users). */
    protected Session login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", password)))).andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.token"), UUID.fromString(JsonPath.read(body, "$.user.tenantId")),
                UUID.fromString(JsonPath.read(body, "$.user.id")), email);
    }

    /** Creates a user with the given role in the admin's tenant and logs them in. */
    protected Session createUser(Session admin, String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        postAs(admin, "/api/users", Map.of("email", email, "fullName", role + " user", "role", role, "password", "password123"));
        return login(email, "password123");
    }

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    protected ResultActions getAs(Session session, String url, Object... vars) throws Exception {
        return mvc.perform(auth(get(url, vars), session));
    }

    protected ResultActions postAs(Session session, String url, Object body, Object... vars) throws Exception {
        MockHttpServletRequestBuilder request = post(url, vars);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return mvc.perform(auth(request, session));
    }

    protected ResultActions putAs(Session session, String url, Object body, Object... vars) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url, vars)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)), session));
    }

    /** POSTs and returns a JSON field of the response, e.g. "$.id". */
    protected String postForField(Session session, String url, Object body, String path, Object... vars) throws Exception {
        String response = postAs(session, url, body, vars).andReturn().getResponse().getContentAsString();
        Object value = JsonPath.read(response, path);
        return String.valueOf(value);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Session session) {
        return session == null ? request : request.header("Authorization", "Bearer " + session.token());
    }
}
