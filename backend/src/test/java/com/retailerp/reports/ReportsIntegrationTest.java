package com.retailerp.reports;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.retailerp.IntegrationTest;

class ReportsIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now().toString();

    private record Shop(Session admin, String warehouse, String tea, String mug) {
    }

    /** Tea: sells 5.00, costs 2.00 (Drinks). Mug: sells 10.00, costs 6.00 (Kitchen). No tax, to keep the maths readable. */
    private Shop shop() throws Exception {
        Session admin = registerTenant("Report Shop");
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "W", "name", "W"), "$.id");
        String tea = postForField(admin, "/api/products", Map.of("sku", "TEA", "name", "Tea", "category", "Drinks",
                "salePrice", "5.00", "costPrice", "2.00"), "$.id");
        String mug = postForField(admin, "/api/products", Map.of("sku", "MUG", "name", "Mug", "category", "Kitchen",
                "salePrice", "10.00", "costPrice", "6.00"), "$.id");
        for (String p : List.of(tea, mug)) {
            postAs(admin, "/api/stock/adjustments", Map.of("productId", p, "warehouseId", warehouse, "quantity", 20, "note", "open"))
                    .andExpect(status().isCreated());
        }
        return new Shop(admin, warehouse, tea, mug);
    }

    private String sell(Shop s, List<Map<String, Object>> lines, String discountPercent) throws Exception {
        String id = postForField(s.admin(), "/api/sales-orders",
                Map.of("warehouseId", s.warehouse(), "discountPercent", discountPercent, "lines", lines), "$.id");
        postAs(s.admin(), "/api/sales-orders/{id}/confirm", null, id).andExpect(status().isOk());
        return id;
    }

    private static Map<String, Object> line(String productId, int qty) {
        return Map.of("productId", productId, "quantity", qty);
    }

    @Test
    void salesProductsAndProfit() throws Exception {
        Shop s = shop();
        sell(s, List.of(line(s.tea(), 4), line(s.mug(), 1)), "0");     // 20 + 10 = 30
        sell(s, List.of(line(s.mug(), 2)), "10");                         // 20 - 2 = 18
        String cancelled = sell(s, List.of(line(s.tea(), 10)), "0");      // cancelled: must not count
        postAs(s.admin(), "/api/sales-orders/{id}/cancel", null, cancelled).andExpect(status().isOk());

        getAs(s.admin(), "/api/reports/sales?from={f}&to={t}&groupBy=DAY", TODAY, TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.totals.orders").value(2))
                .andExpect(jsonPath("$.totals.grossSales").value(50.0))
                .andExpect(jsonPath("$.totals.discounts").value(2.0))
                .andExpect(jsonPath("$.totals.netSales").value(48.0))
                .andExpect(jsonPath("$.totals.averageSale").value(24.0));

        // Mug: 3 sold, net 10 + 18 = 28, cost 18 → profit 10. Tea: 4 sold, net 20, cost 8 → profit 12.
        getAs(s.admin(), "/api/reports/products?from={f}&to={t}&sortBy=QUANTITY", TODAY, TODAY)
                .andExpect(jsonPath("$.rows[0].sku").value("TEA"))
                .andExpect(jsonPath("$.rows[0].quantity").value(4))
                .andExpect(jsonPath("$.rows[0].grossProfit").value(12.0))
                .andExpect(jsonPath("$.rows[0].marginPercent").value(60.0))
                .andExpect(jsonPath("$.rows[1].sku").value("MUG"))
                .andExpect(jsonPath("$.rows[1].netSales").value(28.0))
                .andExpect(jsonPath("$.rows[1].cost").value(18.0))
                .andExpect(jsonPath("$.totals.grossProfit").value(22.0));
        getAs(s.admin(), "/api/reports/products?from={f}&to={t}&sortBy=REVENUE", TODAY, TODAY)
                .andExpect(jsonPath("$.rows[0].sku").value("MUG"));
    }

    /** Changing a product's cost later must not change the profit of past sales. */
    @Test
    void profitUsesTheCostAtTheTimeOfSale() throws Exception {
        Shop s = shop();
        sell(s, List.of(line(s.tea(), 1)), "0"); // cost 2.00 at sale time
        putAs(s.admin(), "/api/products/{id}", Map.of("sku", "TEA", "name", "Tea", "salePrice", "5.00", "costPrice", "4.50"), s.tea())
                .andExpect(status().isOk());
        getAs(s.admin(), "/api/reports/products?from={f}&to={t}", TODAY, TODAY)
                .andExpect(jsonPath("$.rows[0].cost").value(2.0))
                .andExpect(jsonPath("$.rows[0].grossProfit").value(3.0));
    }

    @Test
    void stockValueAtCurrentCost() throws Exception {
        Shop s = shop();
        sell(s, List.of(line(s.mug(), 5)), "0");
        // Tea 20 × 2.00 = 40, Mug 15 × 6.00 = 90
        getAs(s.admin(), "/api/reports/stock-value")
                .andExpect(jsonPath("$.totalValue").value(130.0))
                .andExpect(jsonPath("$.byCategory[0].name").value("Drinks"))
                .andExpect(jsonPath("$.byCategory[0].value").value(40.0))
                .andExpect(jsonPath("$.byCategory[1].value").value(90.0))
                .andExpect(jsonPath("$.byWarehouse[0].quantity").value(35));
    }

    @Test
    void reportsAreForManagersAndPlansWithReports() throws Exception {
        Shop s = shop();
        Session cashier = createUser(s.admin(), "CASHIER");
        getAs(cashier, "/api/reports/stock-value").andExpect(status().isForbidden());
        getAs(s.admin(), "/api/reports/sales?from=2026-12-31&to=2026-01-01").andExpect(status().isBadRequest());
        getAs(s.admin(), "/api/reports/sales?from=2026-01-01").andExpect(status().isBadRequest());

        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "STARTER", "addons", List.of()),
                s.admin().tenantId()).andExpect(status().isOk());
        getAs(s.admin(), "/api/reports/stock-value").andExpect(status().isForbidden()).andExpect(jsonPath("$.module").value("REPORTS"));
    }
}
