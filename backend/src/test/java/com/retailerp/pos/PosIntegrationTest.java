package com.retailerp.pos;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.retailerp.IntegrationTest;

class PosIntegrationTest extends IntegrationTest {

    /** A shop with one warehouse and a product (10.00, 20% tax, 10 in stock). */
    private record Shop(Session admin, String warehouseId, String productId) {
    }

    private Shop shop() throws Exception {
        Session admin = registerTenant("POS Shop");
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "STORE", "name", "Store"), "$.id");
        String product = postForField(admin, "/api/products", Map.of("sku", "MUG", "name", "Mug", "category", "Kitchen",
                "salePrice", "10.00", "costPrice", "4.00", "taxRate", "20"), "$.id");
        postAs(admin, "/api/stock/adjustments", Map.of("productId", product, "warehouseId", warehouse, "quantity", 10, "note", "open"))
                .andExpect(status().isCreated());
        return new Shop(admin, warehouse, product);
    }

    private void openShift(Session who, String warehouseId, String cash) throws Exception {
        postAs(who, "/api/pos/shifts", Map.of("warehouseId", warehouseId, "openingCash", cash)).andExpect(status().isCreated());
    }

    private static Map<String, Object> cashSale(String productId, int qty, String cashReceived) {
        return Map.of("lines", List.of(Map.of("productId", productId, "quantity", qty)), "paymentMethod", "CASH", "cashReceived", cashReceived);
    }

    @Test
    void cashSaleRemovesStockPaysInvoiceAndGivesChange() throws Exception {
        Shop s = shop();
        openShift(s.admin(), s.warehouseId(), "50.00");

        // 2 × 10.00 = 20.00 + 20% tax = 24.00; customer gives 30.00 → change 6.00
        String receipt = postAs(s.admin(), "/api/pos/checkout", cashSale(s.productId(), 2, "30.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(24.0))
                .andExpect(jsonPath("$.change").value(6.0))
                .andExpect(jsonPath("$.shopName").value("POS Shop"))
                .andExpect(jsonPath("$.lines[0].name").value("Mug"))
                .andReturn().getResponse().getContentAsString();
        String invoiceId = com.jayway.jsonpath.JsonPath.read(receipt, "$.invoiceId");

        getAs(s.admin(), "/api/invoices/{id}", invoiceId).andExpect(jsonPath("$.status").value("PAID"));
        getAs(s.admin(), "/api/stock/levels/product/{id}", s.productId()).andExpect(jsonPath("$[0].quantity").value(8));
        getAs(s.admin(), "/api/pos/shifts/current")
                .andExpect(jsonPath("$.salesCount").value(1))
                .andExpect(jsonPath("$.expectedCash").value(74.0)); // 50 float + 24 cash
    }

    @Test
    void notEnoughCashOrStockLeavesNothingBehind() throws Exception {
        Shop s = shop();
        openShift(s.admin(), s.warehouseId(), "0");
        postAs(s.admin(), "/api/pos/checkout", cashSale(s.productId(), 1, "5.00")).andExpect(status().isConflict());
        postAs(s.admin(), "/api/pos/checkout", cashSale(s.productId(), 11, "500.00")).andExpect(status().isConflict());
        // The whole checkout rolled back both times: no stock moved, no sale recorded.
        getAs(s.admin(), "/api/stock/levels/product/{id}", s.productId()).andExpect(jsonPath("$[0].quantity").value(10));
        getAs(s.admin(), "/api/sales-orders").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sellingRequiresAnOpenShiftAndOnlyOneAtATime() throws Exception {
        Shop s = shop();
        postAs(s.admin(), "/api/pos/checkout", cashSale(s.productId(), 1, "20.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Open a shift before selling."));
        getAs(s.admin(), "/api/pos/shifts/current").andExpect(status().isNoContent());
        openShift(s.admin(), s.warehouseId(), "10.00");
        postAs(s.admin(), "/api/pos/shifts", Map.of("warehouseId", s.warehouseId(), "openingCash", "0")).andExpect(status().isConflict());
    }

    @Test
    void discountsReduceTotalsAndCashiersAreLimited() throws Exception {
        Shop s = shop();
        Session cashier = createUser(s.admin(), "CASHIER");
        openShift(cashier, s.warehouseId(), "0");

        // 10% off: 10.00 - 1.00 = 9.00 + 20% tax 1.80 = 10.80
        postAs(cashier, "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.productId(), "quantity", 1)),
                "discountPercent", "10", "paymentMethod", "CARD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountTotal").value(1.0))
                .andExpect(jsonPath("$.taxTotal").value(1.8))
                .andExpect(jsonPath("$.total").value(10.8));
        // Over the 10% limit, by order discount or by lowering the price.
        postAs(cashier, "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.productId(), "quantity", 1)),
                "discountPercent", "20", "paymentMethod", "CARD")).andExpect(status().isConflict());
        postAs(cashier, "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.productId(), "quantity", 1, "unitPrice", "8.00")),
                "paymentMethod", "CARD")).andExpect(status().isConflict());

        // A manager can go further.
        openShift(s.admin(), s.warehouseId(), "0");
        postAs(s.admin(), "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.productId(), "quantity", 1)),
                "discountPercent", "50", "paymentMethod", "CARD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(6.0));
    }

    @Test
    void closingAShiftReconcilesCash() throws Exception {
        Shop s = shop();
        Session cashier = createUser(s.admin(), "CASHIER");
        openShift(cashier, s.warehouseId(), "100.00");
        postAs(cashier, "/api/pos/checkout", cashSale(s.productId(), 1, "12.00")).andExpect(status().isCreated()); // 12.00 cash
        postAs(cashier, "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.productId(), "quantity", 1)),
                "paymentMethod", "CARD")).andExpect(status().isCreated()); // 12.00 card

        String shiftId = com.jayway.jsonpath.JsonPath.read(
                getAs(cashier, "/api/pos/shifts/current").andReturn().getResponse().getContentAsString(), "$.id");
        postAs(cashier, "/api/pos/shifts/{id}/close", Map.of("countedCash", "110.00", "notes", "short"), shiftId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.salesCount").value(2))
                .andExpect(jsonPath("$.salesTotal").value(24.0))
                .andExpect(jsonPath("$.expectedCash").value(112.0))
                .andExpect(jsonPath("$.difference").value(-2.0))
                .andExpect(jsonPath("$.payments.length()").value(2));
        getAs(cashier, "/api/pos/shifts/current").andExpect(status().isNoContent());
        getAs(cashier, "/api/pos/shifts").andExpect(status().isForbidden()); // history is for managers
        getAs(s.admin(), "/api/pos/shifts").andExpect(jsonPath("$.content[0].difference").value(-2.0));
    }

    @Test
    void starterPlanHasNoPointOfSale() throws Exception {
        Shop s = shop();
        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "STARTER", "addons", List.of()),
                s.admin().tenantId()).andExpect(status().isOk());
        getAs(s.admin(), "/api/pos/shifts/current")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.module").value("POINT_OF_SALE"));
        getAs(s.admin(), "/api/products/categories").andExpect(jsonPath("$[0]").value("Kitchen"));
    }
}
