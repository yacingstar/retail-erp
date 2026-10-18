package com.retailerp.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.retailerp.IntegrationTest;
import com.retailerp.tenancy.TenantContext;

class AccountingIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now().toString();
    private static final String PERIOD = "?from=" + TODAY + "&to=" + TODAY;

    @Autowired
    JdbcTemplate jdbc;

    /** Phone: 100.00, TVA 19%, cost 60.00 · Bread: 50.00, TVA 9%, cost 30.00 · 10 of each in stock. */
    private record Shop(Session admin, String warehouse, String phone, String bread, String supplier) {
    }

    private Shop shop() throws Exception {
        Session admin = registerTenant("Compta Shop"); // new tenants get a Pro trial, which includes Accounting
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "W", "name", "W"), "$.id");
        String phone = postForField(admin, "/api/products", Map.of("sku", "PHONE", "name", "Phone", "salePrice", "100.00",
                "costPrice", "60.00", "taxRate", "19"), "$.id");
        String bread = postForField(admin, "/api/products", Map.of("sku", "BREAD", "name", "Bread", "salePrice", "50.00",
                "costPrice", "30.00", "taxRate", "9"), "$.id");
        for (String p : List.of(phone, bread)) {
            postAs(admin, "/api/stock/adjustments", Map.of("productId", p, "warehouseId", warehouse, "quantity", 10, "note", "opening"))
                    .andExpect(status().isCreated());
        }
        String supplier = postForField(admin, "/api/suppliers", Map.of("name", "Grossiste"), "$.id");
        return new Shop(admin, warehouse, phone, bread, supplier);
    }

    private String body(Session s, String url, Object... vars) throws Exception {
        return getAs(s, url, vars).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal num(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof List<?> list) { // filter expressions return a list
            assertThat(list).hasSize(1);
            value = list.get(0);
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static void assertAmount(String json, String path, String expected) {
        assertThat(num(json, path)).as(path).isEqualByComparingTo(expected);
    }

    private static String account(String code) {
        return "$.accounts[?(@.code == '" + code + "')].balance";
    }

    @Test
    void everyBusinessEventPostsABalancedEntryAndTheStatementsAddUp() throws Exception {
        Shop s = shop(); // opening stock: Dr 300 / Cr 758 for 600 + 300

        // Sale: 2 phones + 2 breads = 300.00 + TVA 38.00 + 9.00 = 347.00; cost 180.00. Paid cash.
        String order = postForField(s.admin(), "/api/sales-orders", Map.of("warehouseId", s.warehouse(), "lines", List.of(
                Map.of("productId", s.phone(), "quantity", 2), Map.of("productId", s.bread(), "quantity", 2))), "$.id");
        String confirmed = postAs(s.admin(), "/api/sales-orders/{id}/confirm", null, order).andReturn().getResponse().getContentAsString();
        String invoice = JsonPath.read(confirmed, "$.invoiceId");
        postAs(s.admin(), "/api/invoices/{id}/payments", Map.of("amount", "347.00", "method", "CASH"), invoice).andExpect(status().isOk());

        String saleEntries = body(s.admin(), "/api/accounting/journal/by-source/{id}", order);
        assertThat((List<?>) JsonPath.read(saleEntries, "$")).hasSize(2); // the sale and its cost of goods sold
        assertAmount(saleEntries, "$[0].lines[?(@.accountCode == '411')].debit", "347.00");
        assertAmount(saleEntries, "$[0].lines[?(@.accountCode == '700')].credit", "300.00");
        assertAmount(saleEntries, "$[0].lines[?(@.accountCode == '4457')].credit", "47.00");
        assertAmount(saleEntries, "$[1].lines[?(@.accountCode == '600')].debit", "180.00");

        // Return 1 phone (restocked), refunded in cash: 119.00.
        String returnable = body(s.admin(), "/api/returns/returnable/{id}", order);
        String phoneLine = JsonPath.read(returnable, "$.lines[?(@.productId == '" + s.phone() + "')].salesOrderLineId").toString()
                .replaceAll("[\\[\\]\"]", "");
        postAs(s.admin(), "/api/returns", Map.of("salesOrderId", order, "refundMethod", "CASH",
                "lines", List.of(Map.of("salesOrderLineId", phoneLine, "quantity", 1, "restock", true))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(119.0));

        // Purchase: 5 phones at 60.00 received (300.00), bill with TVA 19% = 357.00, paid by bank.
        String po = postForField(s.admin(), "/api/purchase-orders", Map.of("supplierId", s.supplier(), "warehouseId", s.warehouse(),
                "lines", List.of(Map.of("productId", s.phone(), "quantity", 5, "unitCost", "60.00"))), "$.id");
        postAs(s.admin(), "/api/purchase-orders/{id}/order", null, po).andExpect(status().isOk());
        postAs(s.admin(), "/api/purchase-orders/{id}/receive", null, po).andExpect(status().isOk());
        String bill = body(s.admin(), "/api/bills/by-purchase-order/{id}", po);
        assertAmount(bill, "$.taxTotal", "57.00");
        assertAmount(bill, "$.total", "357.00");
        String billId = JsonPath.read(bill, "$.id");
        postAs(s.admin(), "/api/bills/{id}/approve", null, billId).andExpect(status().isOk());
        postAs(s.admin(), "/api/bills/{id}/payments", Map.of("amount", "357.00", "method", "BANK_TRANSFER"), billId).andExpect(status().isOk());

        // Rent: a manual bill (no TVA) on account 613, approved and left unpaid.
        String rent = postForField(s.admin(), "/api/bills", Map.of("supplierId", s.supplier(), "expenseAccount", "613",
                "lines", List.of(Map.of("description", "Loyer octobre", "quantity", "1", "unitPrice", "500.00"))), "$.id");
        postAs(s.admin(), "/api/bills/{id}/approve", null, rent).andExpect(status().isOk());

        // One bread broken: written off at cost (30.00).
        postAs(s.admin(), "/api/stock/adjustments", Map.of("productId", s.bread(), "warehouseId", s.warehouse(), "quantity", -1, "note", "broken"))
                .andExpect(status().isCreated());

        String tb = body(s.admin(), "/api/accounting/trial-balance" + PERIOD);
        assertThat(num(tb, "$.totalDebit")).isEqualByComparingTo(num(tb, "$.totalCredit"));
        assertAmount(tb, account("530"), "228.00");   // 347 in, 119 refunded
        assertAmount(tb, account("411"), "0.00");     // customer owes nothing
        assertAmount(tb, account("300"), "1050.00");  // 900 opening - 180 sold + 60 back + 300 bought - 30 broken
        assertAmount(tb, account("408"), "0.00");     // goods received are now billed
        assertAmount(tb, account("401"), "500.00");   // only the rent is still owed
        assertAmount(tb, account("512"), "-357.00");  // overdrawn: nobody put money in the bank yet
        assertAmount(tb, account("4457"), "28.00");
        assertAmount(tb, account("4456"), "57.00");

        String pl = body(s.admin(), "/api/accounting/profit-and-loss" + PERIOD);
        assertAmount(pl, "$.totalIncome", "1100.00");  // 200 net sales + 900 opening stock gain
        assertAmount(pl, "$.totalExpenses", "650.00"); // 120 COGS + 500 rent + 30 write-off
        assertAmount(pl, "$.netResult", "450.00");

        String bs = body(s.admin(), "/api/accounting/balance-sheet?date=" + TODAY);
        assertAmount(bs, "$.currentResult", "450.00");
        assertAmount(bs, "$.totalAssets", "978.00");
        assertThat(num(bs, "$.totalAssets")).isEqualByComparingTo(num(bs, "$.totalLiabilitiesAndEquity"));

        String vat = body(s.admin(), "/api/accounting/vat" + PERIOD);
        assertAmount(vat, "$.sales[?(@.rate == 19)].base", "100.00");
        assertAmount(vat, "$.sales[?(@.rate == 19)].tax", "19.00");
        assertAmount(vat, "$.sales[?(@.rate == 9)].tax", "9.00");
        assertAmount(vat, "$.collected", "28.00");
        assertAmount(vat, "$.deductible", "57.00");
        assertAmount(vat, "$.payable", "-29.00"); // a TVA credit to carry forward

        // The owner puts 1000.00 into the bank: a manual entry.
        postAs(s.admin(), "/api/accounting/journal", Map.of("description", "Apport en capital", "lines", List.of(
                Map.of("accountCode", "512", "debit", "1000.00", "credit", "0"),
                Map.of("accountCode", "101", "debit", "0", "credit", "1000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("MANUAL"));
        String ledger = body(s.admin(), "/api/accounting/ledger/512" + PERIOD);
        assertAmount(ledger, "$.closing", "643.00");
        assertThat((List<?>) JsonPath.read(ledger, "$.rows")).hasSize(2);
    }

    @Test
    void cancellingASaleReversesItsEntries() throws Exception {
        Shop s = shop();
        String order = postForField(s.admin(), "/api/sales-orders", Map.of("warehouseId", s.warehouse(),
                "lines", List.of(Map.of("productId", s.phone(), "quantity", 1))), "$.id");
        postAs(s.admin(), "/api/sales-orders/{id}/confirm", null, order).andExpect(status().isOk());
        postAs(s.admin(), "/api/sales-orders/{id}/cancel", null, order).andExpect(status().isOk());

        assertThat((List<?>) JsonPath.read(body(s.admin(), "/api/accounting/journal/by-source/{id}", order), "$")).hasSize(4);
        String tb = body(s.admin(), "/api/accounting/trial-balance" + PERIOD);
        assertAmount(tb, account("411"), "0.00");
        assertAmount(tb, account("700"), "0.00");
        assertAmount(tb, account("300"), "900.00");
    }

    @Test
    void manualEntriesMustBalanceAndAreForAdminsOnly() throws Exception {
        Shop s = shop();
        Map<String, Object> unbalanced = Map.of("description", "Oops", "lines", List.of(
                Map.of("accountCode", "530", "debit", "10.00", "credit", "0"),
                Map.of("accountCode", "101", "debit", "0", "credit", "9.00")));
        postAs(s.admin(), "/api/accounting/journal", unbalanced).andExpect(status().isConflict());
        postAs(s.admin(), "/api/accounting/journal", Map.of("description", "Both sides", "lines", List.of(
                Map.of("accountCode", "530", "debit", "10.00", "credit", "10.00"),
                Map.of("accountCode", "101", "debit", "0", "credit", "0")))).andExpect(status().isBadRequest());
        postAs(s.admin(), "/api/accounting/journal", Map.of("description", "Unknown account", "lines", List.of(
                Map.of("accountCode", "999", "debit", "10.00", "credit", "0"),
                Map.of("accountCode", "101", "debit", "0", "credit", "10.00")))).andExpect(status().isBadRequest());

        Session manager = createUser(s.admin(), "MANAGER");
        Map<String, Object> ok = Map.of("description", "Capital", "lines", List.of(
                Map.of("accountCode", "530", "debit", "10.00", "credit", "0"),
                Map.of("accountCode", "101", "debit", "0", "credit", "10.00")));
        postAs(manager, "/api/accounting/journal", ok).andExpect(status().isForbidden());
        getAs(manager, "/api/accounting/journal" + PERIOD).andExpect(status().isOk()); // managers can read
        getAs(createUser(s.admin(), "CASHIER"), "/api/accounting/journal" + PERIOD).andExpect(status().isForbidden());

        // Admins can add accounts to the chart; codes are unique.
        postAs(s.admin(), "/api/accounting/accounts", Map.of("code", "6131", "name", "Loyer magasin", "type", "EXPENSE"))
                .andExpect(status().isCreated());
        postAs(s.admin(), "/api/accounting/accounts", Map.of("code", "6131", "name", "Again", "type", "EXPENSE"))
                .andExpect(status().isConflict());
    }

    @Test
    void theJournalIsAppendOnlyAndPerTenant() throws Exception {
        Shop a = shop();
        Shop b = shop();
        assertThatThrownBy(() -> TenantContext.callAs(a.admin().tenantId(), () -> jdbc.update("UPDATE journal_lines SET debit = 0")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.callAs(a.admin().tenantId(), () -> jdbc.update("DELETE FROM journal_entries")))
                .rootCause().hasMessageContaining("permission denied");
        Integer visible = TenantContext.callAs(b.admin().tenantId(), () -> jdbc.queryForObject(
                "SELECT count(*) FROM journal_entries WHERE tenant_id = ?", Integer.class, a.admin().tenantId()));
        assertThat(visible).isZero();
        getAs(b.admin(), "/api/accounting/journal" + PERIOD).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void accountingIsAProModule() throws Exception {
        Shop s = shop();
        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "BUSINESS", "addons", List.of()),
                s.admin().tenantId()).andExpect(status().isOk());
        getAs(s.admin(), "/api/accounting/journal" + PERIOD).andExpect(status().isForbidden()).andExpect(jsonPath("$.module").value("ACCOUNTING"));
    }
}
