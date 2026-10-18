package com.retailerp.hr;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.retailerp.common.Money;

/**
 * Algerian payroll arithmetic for one employee and one month. Pure calculation (no database), so it is easy to test
 * and the only place to change when a Finance Law changes the rates.
 * <ul>
 * <li><b>CNAS</b> (social security): 9% paid by the employee, 26% by the employer, on base salary + bonuses.
 * Panier and transport allowances are not subject to CNAS.</li>
 * <li><b>IRG</b> (income tax on salaries), scale of the Loi de finances 2022 (art. 104 CIDTA), on
 * taxable income = CNAS base − employee CNAS + allowances, rounded down to 10 DA:
 * <pre>
 *        0 –  20 000   0%
 *   20 001 –  40 000  23%
 *   40 001 –  80 000  27%
 *   80 001 – 160 000  30%
 *  160 001 – 320 000  33%
 *        &gt; 320 000  35%
 * </pre>
 * minus an allowance of 40% of the tax (at least 1 000 DA, at most 1 500 DA). Incomes up to 30 000 DA pay no IRG;
 * between 30 000 and 35 000 DA a smoothing formula applies (tax × 137/51 − 27 925/8) so there is no jump.</li>
 * </ul>
 * Not handled: the special rules for disabled and retired workers, and other cotisations (mutuelle, etc.).
 */
public final class PayrollRules {

    public static final BigDecimal CNAS_EMPLOYEE_RATE = new BigDecimal("9");
    public static final BigDecimal CNAS_EMPLOYER_RATE = new BigDecimal("26");

    /** {upper bound of the bracket, rate %}; the last bracket has no upper bound. */
    private static final BigDecimal[][] IRG_BRACKETS = {
            { new BigDecimal("20000"), BigDecimal.ZERO },
            { new BigDecimal("40000"), new BigDecimal("23") },
            { new BigDecimal("80000"), new BigDecimal("27") },
            { new BigDecimal("160000"), new BigDecimal("30") },
            { new BigDecimal("320000"), new BigDecimal("33") },
            { null, new BigDecimal("35") },
    };
    private static final BigDecimal ALLOWANCE_RATE = new BigDecimal("0.40");
    private static final BigDecimal ALLOWANCE_MIN = new BigDecimal("1000");
    private static final BigDecimal ALLOWANCE_MAX = new BigDecimal("1500");
    private static final BigDecimal EXEMPT_UP_TO = new BigDecimal("30000");
    private static final BigDecimal SMOOTHING_UNTIL = new BigDecimal("35000");
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PayrollRules() {
    }

    /** Every figure of a payslip. gross = base + bonus + allowance; net = gross − cnasEmployee − irg. */
    public record Calculation(BigDecimal gross, BigDecimal cnasBase, BigDecimal cnasEmployee, BigDecimal taxableIncome,
            BigDecimal irg, BigDecimal netPay, BigDecimal cnasEmployer) {
    }

    public static Calculation calculate(BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowance) {
        BigDecimal cnasBase = Money.round(baseSalary.add(bonus));
        BigDecimal cnasEmployee = Money.percentOf(cnasBase, CNAS_EMPLOYEE_RATE);
        BigDecimal cnasEmployer = Money.percentOf(cnasBase, CNAS_EMPLOYER_RATE);
        BigDecimal taxable = cnasBase.subtract(cnasEmployee).add(allowance);
        // Rounded down to the nearest 10 DA, as the tax administration does.
        taxable = taxable.divide(TEN, 0, RoundingMode.FLOOR).multiply(TEN).setScale(2);
        BigDecimal irg = irg(taxable);
        BigDecimal gross = Money.round(cnasBase.add(allowance));
        return new Calculation(gross, cnasBase, cnasEmployee, taxable, irg, gross.subtract(cnasEmployee).subtract(irg), cnasEmployer);
    }

    /** Monthly IRG on a taxable income. */
    public static BigDecimal irg(BigDecimal taxable) {
        if (taxable.compareTo(EXEMPT_UP_TO) <= 0) {
            return Money.round(BigDecimal.ZERO);
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (BigDecimal[] bracket : IRG_BRACKETS) {
            BigDecimal upper = bracket[0];
            BigDecimal top = upper == null ? taxable : taxable.min(upper);
            if (top.compareTo(lower) > 0) {
                tax = tax.add(top.subtract(lower).multiply(bracket[1]).divide(HUNDRED));
            }
            if (upper == null || taxable.compareTo(upper) <= 0) {
                break;
            }
            lower = upper;
        }
        BigDecimal allowance = tax.multiply(ALLOWANCE_RATE).max(ALLOWANCE_MIN).min(ALLOWANCE_MAX);
        BigDecimal net = tax.subtract(allowance).max(BigDecimal.ZERO);
        if (taxable.compareTo(SMOOTHING_UNTIL) < 0) {
            net = net.multiply(new BigDecimal("137")).divide(new BigDecimal("51"), 10, RoundingMode.HALF_UP)
                    .subtract(new BigDecimal("27925").divide(new BigDecimal("8"))).max(BigDecimal.ZERO);
        }
        return Money.round(net);
    }
}
