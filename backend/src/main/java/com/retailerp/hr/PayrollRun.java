package com.retailerp.hr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.retailerp.common.AuditedEntity;
import com.retailerp.common.BusinessRuleException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** The payroll of one month: a payslip per employee, approved as a whole, then paid. */
@Entity
@Table(name = "payroll_runs")
public class PayrollRun extends AuditedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private SalaryPaymentMethod paymentMethod;

    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_cnas_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCnasEmployee = BigDecimal.ZERO;

    @Column(name = "total_irg", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIrg = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_cnas_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCnasEmployer = BigDecimal.ZERO;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("employeeName")
    private List<Payslip> payslips = new ArrayList<>();

    protected PayrollRun() {
    }

    PayrollRun(String number, YearMonth period, UUID createdBy) {
        this.number = number;
        this.periodYear = period.getYear();
        this.periodMonth = period.getMonthValue();
        this.createdBy = createdBy;
    }

    void addPayslipFor(Employee employee) {
        payslips.add(new Payslip(this, employee));
        recomputeTotals();
    }

    void editPayslip(Payslip payslip, BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowance) {
        requireDraft("adjusted");
        payslip.set(baseSalary, bonus, allowance);
        recomputeTotals();
    }

    void removePayslip(Payslip payslip) {
        requireDraft("changed");
        payslips.remove(payslip);
        recomputeTotals();
    }

    void approve() {
        requireDraft("approved again");
        if (payslips.isEmpty()) {
            throw new BusinessRuleException("Payroll " + number + " has no payslips.");
        }
        status = PayrollStatus.APPROVED;
        approvedAt = Instant.now();
    }

    void markPaid(SalaryPaymentMethod method) {
        if (status != PayrollStatus.APPROVED) {
            throw new BusinessRuleException("Payroll " + number + " is " + status + "; only approved payrolls can be paid.");
        }
        status = PayrollStatus.PAID;
        paymentMethod = method;
        paidAt = Instant.now();
    }

    void requireDraft(String action) {
        if (status != PayrollStatus.DRAFT) {
            throw new BusinessRuleException("Payroll " + number + " is " + status + "; it can't be " + action + ".");
        }
    }

    private void recomputeTotals() {
        totalGross = sum(Payslip::getGross);
        totalCnasEmployee = sum(Payslip::getCnasEmployee);
        totalIrg = sum(Payslip::getIrg);
        totalNet = sum(Payslip::getNetPay);
        totalCnasEmployer = sum(Payslip::getCnasEmployer);
    }

    private BigDecimal sum(Function<Payslip, BigDecimal> field) {
        return payslips.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public YearMonth getPeriod() {
        return YearMonth.of(periodYear, periodMonth);
    }

    public LocalDate getPeriodEnd() {
        return getPeriod().atEndOfMonth();
    }

    public String getNumber() {
        return number;
    }

    public PayrollStatus getStatus() {
        return status;
    }

    public SalaryPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public BigDecimal getTotalCnasEmployee() {
        return totalCnasEmployee;
    }

    public BigDecimal getTotalIrg() {
        return totalIrg;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public BigDecimal getTotalCnasEmployer() {
        return totalCnasEmployer;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public List<Payslip> getPayslips() {
        return payslips;
    }
}
