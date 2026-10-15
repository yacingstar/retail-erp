package com.retailerp.payables;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.retailerp.IntegrationTest;

class SupplierBillsIntegrationTest extends IntegrationTest {

    private record Shop(Session admin, String supplier, String warehouse, String product) {
    }

    private Shop shop() throws Exception {
        Session admin = registerTenant("Bills Shop");
        String supplier = postForField(admin, "/api/suppliers", Map.of("name", "Wholesale Co"), "$.id");
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "W", "name", "W"), "$.id");
        String product = postForField(admin, "/api/products", Map.of("sku", "RICE", "name", "Rice 5kg", "salePrice", "9.00", "costPrice", "5.00"), "$.id");
        return new Shop(admin, supplier, warehouse, product);
    }

    private String receivePo(Shop s, int qty, String unitCost) throws Exception {
        String po = postForField(s.admin(), "/api/purchase-orders", Map.of("supplierId", s.supplier(), "warehouseId", s.warehouse(),
                "lines", List.of(Map.of("productId", s.product(), "quantity", qty, "unitCost", unitCost))), "$.id");
        postAs(s.admin(), "/api/purchase-orders/{id}/order", null, po).andExpect(status().isOk());
        postAs(s.admin(), "/api/purchase-orders/{id}/receive", null, po).andExpect(status().isOk());
        return po;
    }

    private static Map<String, Object> manualBill(String supplierId, String amount) {
        return Map.of("supplierId", supplierId, "supplierReference", "F-2026-118", "dueDate", "2099-12-31",
                "lines", List.of(Map.of("description", "Shop rent, October", "quantity", "1", "unitPrice", amount)));
    }

    @Test
    void receivingGoodsCreatesADraftBillToApproveAndPay() throws Exception {
        Shop s = shop();
        String po = receivePo(s, 10, "4.50"); // 45.00
        String bill = getAs(s.admin(), "/api/bills/by-purchase-order/{id}", po)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("BILL-000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(45.0))
                .andExpect(jsonPath("$.lines[0].description").value("RICE · Rice 5kg"))
                .andReturn().getResponse().getContentAsString();
        String billId = JsonPath.read(bill, "$.id");

        // A draft isn't owed yet and can't be paid.
        getAs(s.admin(), "/api/suppliers/{id}/statement", s.supplier()).andExpect(jsonPath("$.balance").value(0));
        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "10.00", "method", "CASH"), billId).andExpect(status().isConflict());

        // The supplier's paper bill says 46.00 (a delivery fee): fix the draft, then approve.
        putAs(s.admin(), "/api/bills/{id}", Map.of("supplierId", s.supplier(), "supplierReference", "INV-77",
                "lines", List.of(Map.of("description", "Rice 5kg", "quantity", "10", "unitPrice", "4.50"),
                        Map.of("description", "Delivery", "quantity", "1", "unitPrice", "1.00"))), billId)
                .andExpect(jsonPath("$.total").value(46.0));
        postAs(s.admin(), "/api/bills/{id}/approve", null, billId).andExpect(jsonPath("$.status").value("OPEN"));
        putAs(s.admin(), "/api/bills/{id}", manualBill(s.supplier(), "1.00"), billId).andExpect(status().isConflict()); // no edits now

        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "20.00", "method", "BANK_TRANSFER", "reference", "TRF-1"), billId)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue").value(26.0));
        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "30.00", "method", "CASH"), billId).andExpect(status().isConflict());
        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "26.00", "method", "CHEQUE"), billId)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));
        postAs(s.admin(), "/api/bills/{id}/void", null, billId).andExpect(status().isConflict()); // has payments
    }

    @Test
    void manualBillsAndTheSupplierStatement() throws Exception {
        Shop s = shop();
        String rent = postForField(s.admin(), "/api/bills", manualBill(s.supplier(), "800.00"), "$.id");
        String goods = getAs(s.admin(), "/api/bills/by-purchase-order/{id}", receivePo(s, 2, "5.00")).andReturn().getResponse().getContentAsString();
        String goodsId = JsonPath.read(goods, "$.id");
        postAs(s.admin(), "/api/bills/{id}/approve", null, rent).andExpect(status().isOk());
        postAs(s.admin(), "/api/bills/{id}/approve", null, goodsId).andExpect(status().isOk());
        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "300.00", "method", "BANK_TRANSFER"), rent).andExpect(status().isOk());

        getAs(s.admin(), "/api/suppliers/{id}/statement", s.supplier())
                .andExpect(jsonPath("$.totalBilled").value(810.0))
                .andExpect(jsonPath("$.totalPaid").value(300.0))
                .andExpect(jsonPath("$.balance").value(510.0))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[2].balance").value(510.0));
        getAs(s.admin(), "/api/bills?status=OPEN").andExpect(jsonPath("$.totalElements").value(1));
        getAs(s.admin(), "/api/bills?supplierId={id}", s.supplier()).andExpect(jsonPath("$.totalElements").value(2));
        getAs(s.admin(), "/api/dashboard").andExpect(jsonPath("$.outstandingPayables").value(510.0));
    }

    @Test
    void draftsCanBeVoidedAndStatementIgnoresThem() throws Exception {
        Shop s = shop();
        String bill = postForField(s.admin(), "/api/bills", manualBill(s.supplier(), "50.00"), "$.id");
        postAs(s.admin(), "/api/bills/{id}/void", null, bill).andExpect(jsonPath("$.status").value("VOID"));
        postAs(s.admin(), "/api/bills/{id}/approve", null, bill).andExpect(status().isConflict());
        getAs(s.admin(), "/api/suppliers/{id}/statement", s.supplier()).andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void billsAreForManagersOnPlansWithTheModule() throws Exception {
        Shop s = shop();
        Session cashier = createUser(s.admin(), "CASHIER");
        getAs(cashier, "/api/bills").andExpect(status().isForbidden());

        // Business has the module; Starter + Purchasing add-on doesn't: receiving creates no bill there.
        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "STARTER", "addons", List.of("PURCHASING")),
                s.admin().tenantId()).andExpect(status().isOk());
        receivePo(s, 1, "5.00");
        getAs(s.admin(), "/api/bills").andExpect(status().isForbidden()).andExpect(jsonPath("$.module").value("SUPPLIER_BILLS"));
        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "BUSINESS", "addons", List.of()),
                s.admin().tenantId()).andExpect(status().isOk());
        getAs(s.admin(), "/api/bills").andExpect(jsonPath("$.totalElements").value(0));
    }
}
