package com.retailerp.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.retailerp.IntegrationTest;

class PayrollIntegrationTest extends IntegrationTest {

    private static final String TODAY = LocalDate.now().toString();

    private static Map<String, Object> employee(String name, String salary, String hired) {
        return new HashMap<>(Map.of("fullName", name, "jobTitle", "Vendeur", "contractType", "CDI", "hireDate", hired, "baseSalary", salary));
    }

    private static BigDecimal num(String json, String path) {
        Object value = JsonPath.read(json, path);
        if (value instanceof List<?> list) {
            assertThat(list).hasSize(1);
            value = list.get(0);
        }
        return new BigDecimal(String.valueOf(value));
    }

    @Test
    void monthlyPayrollFromDraftToPaidIsPostedToAccounting() throws Exception {
        Session admin = registerTenant("Paie Shop"); // Pro trial includes HR & payroll
        postForField(admin, "/api/employees", employee("Karim Benali", "60000", "2024-01-15"), "$.id");
        String sara = postForField(admin, "/api/employees", employee("Sara Haddad", "36000", "2026-09-10"), "$.id");
        postForField(admin, "/api/employees", employee("Ali Later", "50000", "2026-10-01"), "$.id"); // hired after September
        Map<String, Object> gone = employee("Omar Gone", "50000", "2023-01-01");
        gone.put("endDate", "2026-08-31");
        postForField(admin, "/api/employees", gone, "$.id");
        Map<String, Object> inactive = employee("Nadia Off", "50000", "2023-01-01");
        inactive.put("active", false);
        postForField(admin, "/api/employees", inactive, "$.id");

        String run = postAs(admin, "/api/payroll-runs", Map.of("year", 2026, "month", 9))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("PAY-000001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.payslips.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String runId = JsonPath.read(run, "$.id");
        assertThat(num(run, "$.totalGross")).isEqualByComparingTo("96000");
        assertThat(num(run, "$.totalIrg")).isEqualByComparingTo("8281.58"); // 7 042 + 1 239.58
        assertThat(num(run, "$.totalNet")).isEqualByComparingTo("79078.42");
        postAs(admin, "/api/payroll-runs", Map.of("year", 2026, "month", 9)).andExpect(status().isConflict());

        // Sara gets a 5 000 bonus: her payslip and the totals are recomputed.
        String saraSlip = JsonPath.read(run, "$.payslips[?(@.employeeId == '" + sara + "')].id").toString().replaceAll("[\\[\\]\"]", "");
        String edited = putAs(admin, "/api/payroll-runs/{id}/payslips/{slip}", Map.of("baseSalary", "36000", "bonus", "5000", "allowance", "0"),
                runId, saraSlip).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(num(edited, "$.payslips[?(@.employeeId == '" + sara + "')].netPay")).isEqualByComparingTo("34828.70");
        assertThat(num(edited, "$.totalGross")).isEqualByComparingTo("101000");
        assertThat(num(edited, "$.totalCnasEmployer")).isEqualByComparingTo("26260");

        postAs(admin, "/api/payroll-runs/{id}/pay", Map.of("method", "BANK_TRANSFER"), runId).andExpect(status().isConflict()); // not approved
        postAs(admin, "/api/payroll-runs/{id}/approve", null, runId).andExpect(jsonPath("$.status").value("APPROVED"));
        putAs(admin, "/api/payroll-runs/{id}/payslips/{slip}", Map.of("baseSalary", "1", "bonus", "0", "allowance", "0"), runId, saraSlip)
                .andExpect(status().isConflict());
        deleteAs(admin, "/api/payroll-runs/{id}", runId).andExpect(status().isConflict());

        String entries = getAs(admin, "/api/accounting/journal/by-source/{id}", runId).andReturn().getResponse().getContentAsString();
        assertThat(num(entries, "$[0].lines[?(@.accountCode == '631')].debit")).isEqualByComparingTo("101000");
        assertThat(num(entries, "$[0].lines[?(@.accountCode == '635')].debit")).isEqualByComparingTo("26260");
        assertThat(num(entries, "$[0].lines[?(@.accountCode == '431')].credit")).isEqualByComparingTo("35350"); // 9 090 + 26 260
        assertThat(num(entries, "$[0].lines[?(@.accountCode == '442')].credit")).isEqualByComparingTo("9523.30");
        assertThat(num(entries, "$[0].lines[?(@.accountCode == '421')].credit")).isEqualByComparingTo("82386.70");

        postAs(admin, "/api/payroll-runs/{id}/pay", Map.of("method", "BANK_TRANSFER"), runId).andExpect(jsonPath("$.status").value("PAID"));
        String tb = getAs(admin, "/api/accounting/trial-balance?from=" + TODAY + "&to=" + TODAY).andReturn().getResponse().getContentAsString();
        assertThat(num(tb, "$.accounts[?(@.code == '421')].balance")).isEqualByComparingTo("0");
        assertThat(num(tb, "$.accounts[?(@.code == '512')].balance")).isEqualByComparingTo("-82386.70");

        getAs(admin, "/api/employees/{id}/payslips", sara).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].netPay").value(34828.70));
        getAs(admin, "/api/payroll-runs").andExpect(jsonPath("$.content[0].employees").value(2));
    }

    @Test
    void draftsCanBeTrimmedOrDeleted() throws Exception {
        Session admin = registerTenant("Paie Draft");
        postForField(admin, "/api/employees", employee("Karim", "60000", "2024-01-15"), "$.id");
        postForField(admin, "/api/employees", employee("Sara", "40000", "2024-01-15"), "$.id");
        String run = postAs(admin, "/api/payroll-runs", Map.of("year", 2026, "month", 8)).andReturn().getResponse().getContentAsString();
        String runId = JsonPath.read(run, "$.id");
        String slip = JsonPath.read(run, "$.payslips[0].id");
        deleteAs(admin, "/api/payroll-runs/{id}/payslips/{slip}", runId, slip).andExpect(jsonPath("$.payslips.length()").value(1));
        deleteAs(admin, "/api/payroll-runs/{id}", runId).andExpect(status().isNoContent());
        postAs(admin, "/api/payroll-runs", Map.of("year", 2026, "month", 8)).andExpect(status().isCreated()); // the month is free again
    }

    @Test
    void employeeRulesPreviewAndAccess() throws Exception {
        Session admin = registerTenant("Paie Rules");
        Map<String, Object> karim = employee("Karim", "60000", "2024-01-15");
        karim.put("nss", "123456789012");
        String id = postForField(admin, "/api/employees", karim, "$.id");
        postAs(admin, "/api/employees", karim).andExpect(status().isConflict()); // same NSS
        Map<String, Object> backwards = employee("Bad", "1000", "2024-01-15");
        backwards.put("endDate", "2023-01-01");
        postAs(admin, "/api/employees", backwards).andExpect(status().isConflict());
        postAs(admin, "/api/payroll-runs", Map.of("year", 2020, "month", 1)).andExpect(status().isConflict()); // nobody worked then

        getAs(admin, "/api/payroll/preview?baseSalary=60000").andExpect(jsonPath("$.netPay").value(47558.0))
                .andExpect(jsonPath("$.employerCost").value(75600.0));

        getAs(createUser(admin, "MANAGER"), "/api/employees/{id}", id).andExpect(status().isOk());
        getAs(createUser(admin, "CASHIER"), "/api/employees").andExpect(status().isForbidden());
        getAs(registerTenant("Other Shop"), "/api/employees/{id}", id).andExpect(status().isNotFound());

        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "BUSINESS", "addons", List.of()),
                admin.tenantId()).andExpect(status().isOk());
        getAs(admin, "/api/employees").andExpect(status().isForbidden()).andExpect(jsonPath("$.module").value("HR_PAYROLL"));
    }

    private ResultActions deleteAs(Session session, String url, Object... vars) throws Exception {
        return mvc.perform(delete(url, vars).header("Authorization", "Bearer " + session.token()));
    }
}
