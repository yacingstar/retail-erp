package com.retailerp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** End-to-end business scenarios through the REST API. */
class RetailFlowTest extends IntegrationTest {

    @Test
    void purchaseReceiveSellInvoiceAndPay() throws Exception {
        Session admin = registerTenant("Corner Shop");
        String product = postForField(admin, "/api/products", TestData.product("TEA-1", "4.00", "20"), "$.id");
        String warehouse = postForField(admin, "/api/warehouses", TestData.warehouse("STORE"), "$.id");
        String supplier = postForField(admin, "/api/suppliers", TestData.party("Tea Wholesale"), "$.id");

        // Purchase order: DRAFT → ORDERED → RECEIVED adds 10 to stock.
        Map<String, Object> po = Map.of("supplierId", supplier, "warehouseId", warehouse,
                "lines", List.of(Map.of("productId", product, "quantity", 10, "unitCost", "1.50")));
        String poId = postForField(admin, "/api/purchase-orders", po, "$.id");
        getAs(admin, "/api/purchase-orders/{id}", poId)
                .andExpect(jsonPath("$.number").value("PO-000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(15.0));
        postAs(admin, "/api/purchase-orders/{id}/receive", null, poId).andExpect(status().isConflict()); // not ORDERED yet
        postAs(admin, "/api/purchase-orders/{id}/order", null, poId).andExpect(jsonPath("$.status").value("ORDERED"));
        postAs(admin, "/api/purchase-orders/{id}/receive", null, poId)
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.movements[0].quantity").value(10));
        getAs(admin, "/api/products/{id}", product).andExpect(jsonPath("$.costPrice").value(1.5));
        getAs(admin, "/api/stock/levels/product/{id}", product).andExpect(jsonPath("$[0].quantity").value(10));

        // A cashier sells 3 (price 4.00 + 20% tax = 14.40 total).
        Session cashier = createUser(admin, "CASHIER");
        String customer = postForField(cashier, "/api/customers", TestData.party("Jane Doe"), "$.id");
        Map<String, Object> so = Map.of("customerId", customer, "warehouseId", warehouse, "lines", List.of(TestData.line(product, 3)));
        String soId = postForField(cashier, "/api/sales-orders", so, "$.id");
        String confirmed = postAs(cashier, "/api/sales-orders/{id}/confirm", null, soId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.subtotal").value(12.0))
                .andExpect(jsonPath("$.taxTotal").value(2.4))
                .andExpect(jsonPath("$.total").value(14.4))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-000001"))
                .andReturn().getResponse().getContentAsString();
        getAs(admin, "/api/stock/levels/product/{id}", product).andExpect(jsonPath("$[0].quantity").value(7));

        // Pay the invoice in two parts.
        String invoiceId = JsonPath.read(confirmed, "$.invoiceId");
        postAs(cashier, "/api/invoices/{id}/payments", Map.of("amount", "10.00", "method", "CASH"), invoiceId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue").value(4.4));
        postAs(cashier, "/api/invoices/{id}/payments", Map.of("amount", "5.00", "method", "CARD"), invoiceId)
                .andExpect(status().isConflict()); // overpayment
        postAs(cashier, "/api/invoices/{id}/payments", Map.of("amount", "4.40", "method", "CARD"), invoiceId)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));

        // A paid sale can't be cancelled (the invoice can't be voided).
        postAs(admin, "/api/sales-orders/{id}/cancel", null, soId).andExpect(status().isConflict());

        getAs(admin, "/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").value(14.4))
                .andExpect(jsonPath("$.outstandingReceivables").value(0))
                .andExpect(jsonPath("$.recentSales[0].number").value(startsWith("SO-")));
    }

    @Test
    void cannotSellMoreThanInStock() throws Exception {
        Session admin = registerTenant("Shop");
        String product = postForField(admin, "/api/products", TestData.product("P-1", "1.00", "0"), "$.id");
        String warehouse = postForField(admin, "/api/warehouses", TestData.warehouse("W"), "$.id");
        postAs(admin, "/api/stock/adjustments", TestData.adjustment(product, warehouse, 2)).andExpect(status().isCreated());

        // Same product on two lines: 1 + 2 = 3 > 2 in stock.
        String soId = postForField(admin, "/api/sales-orders",
                TestData.salesOrder(warehouse, List.of(TestData.line(product, 1), TestData.line(product, 2))), "$.id");
        postAs(admin, "/api/sales-orders/{id}/confirm", null, soId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Not enough stock for P-1 in W: available 2, requested 3."));
        getAs(admin, "/api/sales-orders/{id}", soId).andExpect(jsonPath("$.status").value("DRAFT"));
        getAs(admin, "/api/stock/levels/product/{id}", product).andExpect(jsonPath("$[0].quantity").value(2));

        // Negative adjustment below zero is also rejected.
        postAs(admin, "/api/stock/adjustments", TestData.adjustment(product, warehouse, -3)).andExpect(status().isConflict());
    }

    @Test
    void cancellingAConfirmedSaleReturnsTheStockAndVoidsTheInvoice() throws Exception {
        Session admin = registerTenant("Shop");
        String product = postForField(admin, "/api/products", TestData.product("P-1", "5.00", "0"), "$.id");
        String warehouse = postForField(admin, "/api/warehouses", TestData.warehouse("W"), "$.id");
        postAs(admin, "/api/stock/adjustments", TestData.adjustment(product, warehouse, 5));
        String soId = postForField(admin, "/api/sales-orders", TestData.salesOrder(warehouse, List.of(TestData.line(product, 4))), "$.id");
        String invoiceId = postForField(admin, "/api/sales-orders/{id}/confirm", null, "$.invoiceId", soId);

        Session cashier = createUser(admin, "CASHIER");
        postAs(cashier, "/api/sales-orders/{id}/cancel", null, soId).andExpect(status().isForbidden());

        postAs(admin, "/api/sales-orders/{id}/cancel", null, soId)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.movements.length()").value(2)); // SALE -4, SALE_RETURN +4 (history kept)
        getAs(admin, "/api/invoices/{id}", invoiceId).andExpect(jsonPath("$.status").value("VOID"));
        getAs(admin, "/api/stock/levels/product/{id}", product).andExpect(jsonPath("$[0].quantity").value(5));
    }

    @Test
    void transferMovesStockBetweenWarehouses() throws Exception {
        Session admin = registerTenant("Shop");
        String product = postForField(admin, "/api/products", TestData.product("P-1", "5.00", "0"), "$.id");
        String w1 = postForField(admin, "/api/warehouses", TestData.warehouse("A"), "$.id");
        String w2 = postForField(admin, "/api/warehouses", TestData.warehouse("B"), "$.id");
        postAs(admin, "/api/stock/adjustments", TestData.adjustment(product, w1, 10));

        postAs(admin, "/api/stock/transfers", Map.of("productId", product, "fromWarehouseId", w1, "toWarehouseId", w2, "quantity", 4))
                .andExpect(status().isCreated());
        getAs(admin, "/api/stock/levels/product/{id}", product)
                .andExpect(jsonPath("$[0].warehouseCode").value("A")).andExpect(jsonPath("$[0].quantity").value(6))
                .andExpect(jsonPath("$[1].warehouseCode").value("B")).andExpect(jsonPath("$[1].quantity").value(4));
        getAs(admin, "/api/stock/low").andExpect(jsonPath("$.length()").value(0)); // 10 on hand > reorder level 5
    }

    @Test
    void cashierCannotManageCatalogOrPurchasing() throws Exception {
        Session admin = registerTenant("Shop");
        Session cashier = createUser(admin, "CASHIER");
        postAs(cashier, "/api/products", TestData.product("X", "1.00", "0")).andExpect(status().isForbidden());
        getAs(cashier, "/api/purchase-orders").andExpect(status().isForbidden());
        getAs(cashier, "/api/products").andExpect(status().isOk());
        Session manager = createUser(admin, "MANAGER");
        postAs(manager, "/api/products", TestData.product("X", "1.00", "0")).andExpect(status().isCreated());
        getAs(manager, "/api/users").andExpect(status().isForbidden());
    }

    /** Two cashiers try to sell the last 5 units at the same moment: exactly one may succeed. */
    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Session admin = registerTenant("Busy Shop");
        String product = postForField(admin, "/api/products", TestData.product("HOT", "1.00", "0"), "$.id");
        String warehouse = postForField(admin, "/api/warehouses", TestData.warehouse("W"), "$.id");
        postAs(admin, "/api/stock/adjustments", TestData.adjustment(product, warehouse, 5));

        int attempts = 6;
        List<String> orderIds = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            orderIds.add(postForField(admin, "/api/sales-orders", TestData.salesOrder(warehouse, List.of(TestData.line(product, 5))), "$.id"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Integer>> tasks = orderIds.stream()
                    .<Callable<Integer>>map(id -> () -> postAs(admin, "/api/sales-orders/{id}/confirm", null, id).andReturn().getResponse().getStatus())
                    .toList();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
        } finally {
            pool.shutdown();
        }
        getAs(admin, "/api/stock/levels/product/{id}", product).andExpect(jsonPath("$[0].quantity").value(0));
    }
}
