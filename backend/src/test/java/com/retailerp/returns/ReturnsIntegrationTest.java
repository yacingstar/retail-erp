package com.retailerp.returns;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.retailerp.IntegrationTest;

class ReturnsIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now().toString();

    /** Mug: 10.00, 20% tax, cost 4.00, 20 in stock. */
    private record Shop(Session admin, String warehouse, String mug, String customer) {
    }

    private Shop shop() throws Exception {
        Session admin = registerTenant("Returns Shop");
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "W", "name", "W"), "$.id");
        String mug = postForField(admin, "/api/products", Map.of("sku", "MUG", "name", "Mug", "salePrice", "10.00", "costPrice", "4.00",
                "taxRate", "20"), "$.id");
        postAs(admin, "/api/stock/adjustments", Map.of("productId", mug, "warehouseId", warehouse, "quantity", 20, "note", "open"))
                .andExpect(status().isCreated());
        String customer = postForField(admin, "/api/customers", Map.of("name", "Nadia"), "$.id");
        return new Shop(admin, warehouse, mug, customer);
    }

    /** Sells qty mugs; returns {orderId, invoiceId, firstLineId}. Pays in full when paidWith != null. */
    private String[] sale(Shop s, int qty, String customerId, String paidWith) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("warehouseId", s.warehouse(), "lines", List.of(Map.of("productId", s.mug(), "quantity", qty))));
        if (customerId != null) {
            body.put("customerId", customerId);
        }
        String orderId = postForField(s.admin(), "/api/sales-orders", body, "$.id");
        String confirmed = postAs(s.admin(), "/api/sales-orders/{id}/confirm", null, orderId).andReturn().getResponse().getContentAsString();
        String invoiceId = JsonPath.read(confirmed, "$.invoiceId");
        if (paidWith != null) {
            Object total = JsonPath.read(confirmed, "$.total");
            postAs(s.admin(), "/api/invoices/{id}/payments", Map.of("amount", total, "method", paidWith), invoiceId).andExpect(status().isOk());
        }
        String returnable = getAs(s.admin(), "/api/returns/returnable/{id}", orderId).andReturn().getResponse().getContentAsString();
        return new String[] { orderId, invoiceId, JsonPath.read(returnable, "$.lines[0].salesOrderLineId") };
    }

    private Map<String, Object> returnOf(String orderId, String lineId, int qty, boolean restock, String refundMethod) {
        Map<String, Object> body = new HashMap<>(Map.of("salesOrderId", orderId,
                "lines", List.of(Map.of("salesOrderLineId", lineId, "quantity", qty, "restock", restock))));
        if (refundMethod != null) {
            body.put("refundMethod", refundMethod);
        }
        return body;
    }

    private void level(Shop s, int expected) throws Exception {
        getAs(s.admin(), "/api/stock/levels/product/{id}", s.mug()).andExpect(jsonPath("$[0].quantity").value(expected));
    }

    @Test
    void partialReturnsOfAPaidSaleRefundExactlyTheTotal() throws Exception {
        Shop s = shop();
        String[] sale = sale(s, 3, null, "CARD"); // 30.00 + 6.00 tax = 36.00
        level(s, 17);

        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, "CASH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("CN-000001"))
                .andExpect(jsonPath("$.total").value(12.0))
                .andExpect(jsonPath("$.refundAmount").value(12.0))
                .andExpect(jsonPath("$.refundMethod").value("CASH"));
        level(s, 18);
        getAs(s.admin(), "/api/returns/returnable/{id}", sale[0]).andExpect(jsonPath("$.lines[0].returnable").value(2));

        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 2, true, "CARD"))
                .andExpect(jsonPath("$.refundAmount").value(24.0));
        getAs(s.admin(), "/api/invoices/{id}", sale[1])
                .andExpect(jsonPath("$.amountCredited").value(36.0))
                .andExpect(jsonPath("$.amountRefunded").value(36.0))
                .andExpect(jsonPath("$.balanceDue").value(0));
        // Nothing left to return; a sale with returns can't be cancelled any more.
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, "CARD")).andExpect(status().isConflict());
        postAs(s.admin(), "/api/sales-orders/{id}/cancel", null, sale[0]).andExpect(status().isConflict());
    }

    @Test
    void returnOnAnUnpaidInvoiceJustLowersWhatIsOwed() throws Exception {
        Shop s = shop();
        String[] sale = sale(s, 2, null, null); // 24.00 owed, nothing paid
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, false, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundAmount").value(0));
        level(s, 18); // not restocked: the damaged mug doesn't come back
        getAs(s.admin(), "/api/invoices/{id}", sale[1])
                .andExpect(jsonPath("$.balanceDue").value(12.0))
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, null)).andExpect(status().isCreated());
        getAs(s.admin(), "/api/invoices/{id}", sale[1]).andExpect(jsonPath("$.status").value("CREDITED"));
    }

    @Test
    void paidSaleNeedsARefundMethod() throws Exception {
        Shop s = shop();
        String[] sale = sale(s, 1, null, "CASH");
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, null)).andExpect(status().isConflict());
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, "STORE_CREDIT"))
                .andExpect(status().isConflict()); // walk-in: no store credit
        level(s, 19); // both rolled back
    }

    @Test
    void storeCreditCanBeEarnedAndSpent() throws Exception {
        Shop s = shop();
        String[] first = sale(s, 2, s.customer(), "CARD"); // 24.00
        postAs(s.admin(), "/api/returns", returnOf(first[0], first[2], 1, true, "STORE_CREDIT"))
                .andExpect(jsonPath("$.refundMethod").value("STORE_CREDIT"));
        getAs(s.admin(), "/api/customers/{id}/store-credit", s.customer()).andExpect(jsonPath("$.balance").value(12.0));

        String[] second = sale(s, 2, s.customer(), null); // 24.00 owed
        postAs(s.admin(), "/api/invoices/{id}/payments", Map.of("amount", "20.00", "method", "STORE_CREDIT"), second[1])
                .andExpect(status().isConflict()); // only 12.00 available
        postAs(s.admin(), "/api/invoices/{id}/payments", Map.of("amount", "12.00", "method", "STORE_CREDIT"), second[1])
                .andExpect(jsonPath("$.balanceDue").value(12.0));
        getAs(s.admin(), "/api/customers/{id}/store-credit", s.customer())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void cashRefundAtTheTillLeavesTheDrawer() throws Exception {
        Shop s = shop();
        postAs(s.admin(), "/api/pos/shifts", Map.of("warehouseId", s.warehouse(), "openingCash", "100.00")).andExpect(status().isCreated());
        String receipt = postAs(s.admin(), "/api/pos/checkout", Map.of("lines", List.of(Map.of("productId", s.mug(), "quantity", 1)),
                "paymentMethod", "CASH", "cashReceived", "12.00")).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(receipt, "$.salesOrderId");
        String lineId = JsonPath.read(getAs(s.admin(), "/api/returns/returnable/{id}", orderId).andReturn().getResponse().getContentAsString(),
                "$.lines[0].salesOrderLineId");
        postAs(s.admin(), "/api/returns", returnOf(orderId, lineId, 1, true, "CASH")).andExpect(status().isCreated());
        getAs(s.admin(), "/api/pos/shifts/current")
                .andExpect(jsonPath("$.cashRefunds").value(12.0))
                .andExpect(jsonPath("$.expectedCash").value(100.0)); // 100 + 12 sold − 12 refunded
    }

    @Test
    void reportsAreNetOfReturns() throws Exception {
        Shop s = shop();
        String[] sale = sale(s, 3, null, "CARD"); // net 30.00, cost 12.00
        postAs(s.admin(), "/api/returns", returnOf(sale[0], sale[2], 1, true, "CARD")).andExpect(status().isCreated());
        getAs(s.admin(), "/api/reports/sales?from={f}&to={t}", TODAY, TODAY)
                .andExpect(jsonPath("$.totals.grossSales").value(30.0))
                .andExpect(jsonPath("$.totals.returns").value(10.0))
                .andExpect(jsonPath("$.totals.netSales").value(20.0))
                .andExpect(jsonPath("$.totals.total").value(24.0))
                .andExpect(jsonPath("$.totals.averageSale").value(36.0));
        getAs(s.admin(), "/api/reports/products?from={f}&to={t}", TODAY, TODAY)
                .andExpect(jsonPath("$.rows[0].quantity").value(2))
                .andExpect(jsonPath("$.rows[0].netSales").value(20.0))
                .andExpect(jsonPath("$.rows[0].cost").value(8.0));
    }

    @Test
    void cashiersCanProcessReturns() throws Exception {
        Shop s = shop();
        String[] sale = sale(s, 1, null, "CASH");
        Session cashier = createUser(s.admin(), "CASHIER");
        postAs(cashier, "/api/returns", returnOf(sale[0], sale[2], 1, true, "CASH")).andExpect(status().isCreated());
        getAs(cashier, "/api/returns").andExpect(jsonPath("$.totalElements").value(1));
    }
}
