package com.retailerp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.retailerp.tenancy.TenantContext;

/**
 * The most important tests of a multi-tenant system: tenant B must never see or touch tenant A's data.
 * Checked at two levels: through the API (Hibernate @TenantId) and with raw SQL (Postgres RLS alone).
 */
class TenantIsolationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void tenantsCannotSeeEachOthersData() throws Exception {
        Session a = registerTenant("Tenant A");
        Session b = registerTenant("Tenant B");
        String productA = postForField(a, "/api/products", TestData.product("SKU-1", "10.00", "0"), "$.id");

        getAs(a, "/api/products/{id}", productA).andExpect(status().isOk());
        getAs(b, "/api/products/{id}", productA).andExpect(status().isNotFound());
        getAs(b, "/api/products").andExpect(jsonPath("$.totalElements").value(0));
        putAs(b, "/api/products/{id}", TestData.product("HACKED", "0.00", "0"), productA).andExpect(status().isNotFound());

        // Same SKU in another tenant is fine: uniqueness is per tenant.
        postAs(b, "/api/products", TestData.product("SKU-1", "12.00", "0"), new Object[0]).andExpect(status().isCreated());
        getAs(a, "/api/products/{id}", productA).andExpect(jsonPath("$.sku").value("SKU-1")).andExpect(jsonPath("$.salePrice").value(10.0));
    }

    @Test
    void tenantCannotUseAnotherTenantsRecordsInItsDocuments() throws Exception {
        Session a = registerTenant("Tenant A");
        Session b = registerTenant("Tenant B");
        String productA = postForField(a, "/api/products", TestData.product("A-1", "10.00", "0"), "$.id");
        String warehouseB = postForField(b, "/api/warehouses", TestData.warehouse("MAIN"), "$.id");

        postAs(b, "/api/sales-orders", TestData.salesOrder(warehouseB, List.of(TestData.line(productA, 1))))
                .andExpect(status().isNotFound());
        postAs(b, "/api/stock/adjustments", TestData.adjustment(productA, warehouseB, 5)).andExpect(status().isNotFound());
    }

    /** Bypass Hibernate entirely: plain SQL through the app's DataSource, so only RLS is protecting us. */
    @Test
    void rowLevelSecurityFiltersRawSql() throws Exception {
        Session a = registerTenant("Tenant A");
        Session b = registerTenant("Tenant B");
        postForField(a, "/api/products", TestData.product("RLS-A", "1.00", "0"), "$.id");

        Integer seenByA = TenantContext.callAs(a.tenantId(), () -> jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        Integer seenByB = TenantContext.callAs(b.tenantId(), () -> jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        Integer seenByNobody = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
        assertThat(seenByA).isEqualTo(1);
        assertThat(seenByB).isZero();
        assertThat(seenByNobody).isZero();

        Integer usersSeenByB = TenantContext.callAs(b.tenantId(), () -> jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
        assertThat(usersSeenByB).isEqualTo(1);
    }

    @Test
    void rowLevelSecurityRejectsWritesIntoAnotherTenant() throws Exception {
        Session a = registerTenant("Tenant A");
        Session b = registerTenant("Tenant B");
        // Logged in as B, try to insert a row that claims to belong to A.
        assertThatThrownBy(() -> TenantContext.callAs(b.tenantId(), () -> jdbc.update(
                "INSERT INTO warehouses (id, tenant_id, code, name) VALUES (?, ?, 'X', 'Sneaky')", UUID.randomUUID(), a.tenantId())))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void stockMovementsAreAppendOnly() throws Exception {
        Session a = registerTenant("Tenant A");
        String product = postForField(a, "/api/products", TestData.product("AO-1", "1.00", "0"), "$.id");
        String warehouse = postForField(a, "/api/warehouses", TestData.warehouse("W1"), "$.id");
        postAs(a, "/api/stock/adjustments", TestData.adjustment(product, warehouse, 10)).andExpect(status().isCreated());

        assertThatThrownBy(() -> TenantContext.callAs(a.tenantId(), () -> jdbc.update("UPDATE stock_movements SET quantity = 1000")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.callAs(a.tenantId(), () -> jdbc.update("DELETE FROM stock_movements")))
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void loginTokenOfOneTenantOnlyReachesThatTenant() throws Exception {
        Session a = registerTenant("Tenant A");
        Session b = registerTenant("Tenant B");
        getAs(a, "/api/users").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value(a.email()));
        getAs(b, "/api/users").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value(b.email()));
        postAs(a, "/api/users", Map.of("email", "x-" + UUID.randomUUID() + "@example.com", "fullName", "X", "role", "CASHIER",
                "password", "password123")).andExpect(status().isCreated());
        getAs(b, "/api/users").andExpect(jsonPath("$.length()").value(1));
    }
}
