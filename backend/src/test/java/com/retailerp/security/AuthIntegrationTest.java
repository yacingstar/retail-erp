package com.retailerp.security;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.retailerp.IntegrationTest;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginAndMe() throws Exception {
        Session session = registerTenant("Acme Shop");

        getAs(session, "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenantName").value("Acme Shop"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", session.email().toUpperCase(), "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.user.tenantId").value(session.tenantId().toString()));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreRejectedTheSameWay() throws Exception {
        Session session = registerTenant("Shop");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", session.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", "nobody@example.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void duplicateEmailIsRejectedEvenAcrossTenants() throws Exception {
        Session session = registerTenant("First");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("companyName", "Second", "fullName", "X", "email", session.email(), "password", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("companyName", "", "fullName", "X", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()));
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateCashierWhoCannotManageUsers() throws Exception {
        Session admin = registerTenant("Shop");
        String email = "cashier-" + System.nanoTime() + "@example.com";
        postAs(admin, "/api/users", Map.of("email", email, "fullName", "Cass", "role", "CASHIER", "password", "password123"))
                .andExpect(status().isCreated());

        Session cashier = login(email, "password123");

        getAs(cashier, "/api/users").andExpect(status().isForbidden());
        getAs(admin, "/api/users").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
