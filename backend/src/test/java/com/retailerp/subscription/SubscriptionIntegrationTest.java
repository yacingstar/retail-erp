package com.retailerp.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.retailerp.IntegrationTest;
import com.retailerp.tenancy.TenantContext;

class SubscriptionIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private static Map<String, Object> warehouse(String code) {
        return Map.of("code", code, "name", "Warehouse " + code);
    }

    /** Moves the tenant to a plan with no trial, as the platform admin would. */
    private void setPlan(Session platform, Session tenant, String plan, List<String> addons) throws Exception {
        putAs(platform, "/api/platform/tenants/{id}/subscription", Map.of("planCode", plan, "addons", addons), tenant.tenantId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value(plan))
                .andExpect(jsonPath("$.trialActive").value(false));
    }

    @Test
    void newTenantStartsWithAProTrial() throws Exception {
        Session shop = registerTenant("Trial Shop");
        getAs(shop, "/api/subscription")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"))
                .andExpect(jsonPath("$.basePlanCode").value("STARTER"))
                .andExpect(jsonPath("$.trialActive").value(true))
                .andExpect(jsonPath("$.trialEndsAt", notNullValue()))
                .andExpect(jsonPath("$.modules", hasItem("PURCHASING")))
                .andExpect(jsonPath("$.plans.length()").value(3));
        getAs(shop, "/api/purchase-orders").andExpect(status().isOk());
    }

    @Test
    void modulesOutsideThePlanAreBlockedUntilAddedAsAddOn() throws Exception {
        Session platform = platformAdmin();
        Session shop = registerTenant("Starter Shop");
        setPlan(platform, shop, "STARTER", List.of());

        getAs(shop, "/api/subscription").andExpect(jsonPath("$.modules", not(hasItem("PURCHASING"))));
        getAs(shop, "/api/purchase-orders")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_IN_PLAN"))
                .andExpect(jsonPath("$.module").value("PURCHASING"));
        getAs(shop, "/api/suppliers").andExpect(status().isForbidden());
        getAs(shop, "/api/products").andExpect(status().isOk()); // core modules stay available

        setPlan(platform, shop, "STARTER", List.of("PURCHASING"));
        getAs(shop, "/api/purchase-orders").andExpect(status().isOk());
        getAs(shop, "/api/subscription").andExpect(jsonPath("$.addons", hasItem("PURCHASING")));

        setPlan(platform, shop, "BUSINESS", List.of());
        getAs(shop, "/api/purchase-orders").andExpect(status().isOk());
    }

    @Test
    void starterLimitsUsersAndWarehouses() throws Exception {
        Session platform = platformAdmin();
        Session shop = registerTenant("Small Shop");
        setPlan(platform, shop, "STARTER", List.of()); // 2 users, 1 warehouse

        postAs(shop, "/api/warehouses", warehouse("W1")).andExpect(status().isCreated());
        postAs(shop, "/api/warehouses", warehouse("W2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.limit").value("warehouses"));
        // An inactive warehouse doesn't count.
        postAs(shop, "/api/warehouses", Map.of("code", "OLD", "name", "Archived", "active", false)).andExpect(status().isCreated());

        Map<String, Object> user = Map.of("email", "u-" + UUID.randomUUID() + "@example.com", "fullName", "U", "role", "CASHIER",
                "password", "password123");
        postAs(shop, "/api/users", user).andExpect(status().isCreated()); // admin + 1 = 2
        postAs(shop, "/api/users", Map.of("email", "v-" + UUID.randomUUID() + "@example.com", "fullName", "V", "role", "CASHIER",
                "password", "password123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limit").value("users"));
        getAs(shop, "/api/subscription").andExpect(jsonPath("$.activeUsers").value(2)).andExpect(jsonPath("$.activeWarehouses").value(1));

        setPlan(platform, shop, "PRO", List.of()); // unlimited
        postAs(shop, "/api/warehouses", warehouse("W3")).andExpect(status().isCreated());
    }

    @Test
    void platformAdminSeesAllTenantsButTenantsCannotUsePlatformApi() throws Exception {
        Session platform = platformAdmin();
        Session a = registerTenant("Platform View A");
        Session b = registerTenant("Platform View B");

        getAs(platform, "/api/platform/tenants")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(a.tenantId().toString())))
                .andExpect(jsonPath("$[*].id", hasItem(b.tenantId().toString())));
        getAs(platform, "/api/platform/plans").andExpect(jsonPath("$[0].code").value("STARTER"));

        getAs(a, "/api/platform/tenants").andExpect(status().isForbidden());
        putAs(a, "/api/platform/tenants/{id}/subscription", Map.of("planCode", "PRO", "addons", List.of()), a.tenantId())
                .andExpect(status().isForbidden());
        // A platform token is not a tenant user.
        getAs(platform, "/api/products").andExpect(status().isForbidden());
    }

    @Test
    void platformLoginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", PLATFORM_EMAIL, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPlanIsRejected() throws Exception {
        Session platform = platformAdmin();
        Session shop = registerTenant("Shop");
        putAs(platform, "/api/platform/tenants/{id}/subscription", Map.of("planCode", "GOLD", "addons", List.of()), shop.tenantId())
                .andExpect(status().isBadRequest());
        putAs(platform, "/api/platform/tenants/{id}/subscription", Map.of("planCode", "PRO", "addons", List.of()), UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    /** Even with raw SQL, the app's database role can't upgrade a tenant: no UPDATE privilege. */
    @Test
    void tenantCannotChangeItsOwnPlanInTheDatabase() throws Exception {
        Session shop = registerTenant("Sneaky Shop");
        assertThatThrownBy(() -> TenantContext.callAs(shop.tenantId(),
                () -> jdbc.update("UPDATE tenant_subscriptions SET plan_code = 'PRO'")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.callAs(shop.tenantId(),
                () -> jdbc.update("INSERT INTO tenant_addons (tenant_id, module) VALUES (?, 'PURCHASING')", shop.tenantId())))
                .rootCause().hasMessageContaining("permission denied");
    }
}
