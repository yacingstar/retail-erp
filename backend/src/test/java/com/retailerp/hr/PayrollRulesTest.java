package com.retailerp.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.retailerp.hr.PayrollRules.Calculation;

/** Plain unit test: no Spring, no database. Expected amounts worked out by hand from the IRG scale. */
class PayrollRulesTest {

    private static Calculation calc(String base, String bonus, String allowance) {
        return PayrollRules.calculate(new BigDecimal(base), new BigDecimal(bonus), new BigDecimal(allowance));
    }

    private static BigDecimal irg(String taxable) {
        return PayrollRules.irg(new BigDecimal(taxable));
    }

    @Test
    void typicalSalary() {
        // CNAS 9% = 5 400; taxable 54 600; IRG = 20 000×23% + 14 600×27% = 8 542, minus 1 500 (40% capped) = 7 042.
        Calculation c = calc("60000", "0", "0");
        assertThat(c.cnasEmployee()).isEqualByComparingTo("5400");
        assertThat(c.taxableIncome()).isEqualByComparingTo("54600");
        assertThat(c.irg()).isEqualByComparingTo("7042");
        assertThat(c.netPay()).isEqualByComparingTo("47558");
        assertThat(c.cnasEmployer()).isEqualByComparingTo("15600");
    }

    @Test
    void allowancesAreTaxedButNotSubjectToCnas() {
        // CNAS base 45 000 (base + bonus); taxable = 45 000 − 4 050 + 3 000 = 43 950; IRG = 4 600 + 3 950×27% − 1 500.
        Calculation c = calc("40000", "5000", "3000");
        assertThat(c.cnasBase()).isEqualByComparingTo("45000");
        assertThat(c.cnasEmployee()).isEqualByComparingTo("4050");
        assertThat(c.taxableIncome()).isEqualByComparingTo("43950");
        assertThat(c.irg()).isEqualByComparingTo("4166.50");
        assertThat(c.gross()).isEqualByComparingTo("48000");
        assertThat(c.netPay()).isEqualByComparingTo("39783.50");
        assertThat(c.cnasEmployer()).isEqualByComparingTo("11700");
    }

    @Test
    void taxableIncomeIsRoundedDownToTenDinars() {
        Calculation c = calc("50005", "0", "0"); // 50 005 − 4 500.45 = 45 504.55 → 45 500
        assertThat(c.taxableIncome()).isEqualByComparingTo("45500");
        assertThat(c.irg()).isEqualByComparingTo("4585");
    }

    @Test
    void lowIncomesAreExemptAndTheSmoothingZoneHasNoJump() {
        assertThat(calc("25000", "0", "0").irg()).isEqualByComparingTo("0");
        assertThat(irg("30000")).isEqualByComparingTo("0");
        assertThat(irg("30010")).isEqualByComparingTo("7.71");     // (2 302.30 − 1 000) × 137/51 − 27 925/8 = 3 498.34 − 3 490.63
        assertThat(irg("32760")).isEqualByComparingTo("1239.58");  // (2 934.80 − 1 173.92) × 137/51 − 3 490.625
        assertThat(irg("34990")).isLessThan(irg("35000"));
        assertThat(irg("35000")).isEqualByComparingTo("2070");     // 3 450 − 40%
    }

    @Test
    void highIncomesUseEveryBracket() {
        // 4 600 + 10 800 + 24 000 + 52 800 + 44 000 × 35% = 107 600, minus the 1 500 cap.
        assertThat(irg("364000")).isEqualByComparingTo("106100");
    }
}
