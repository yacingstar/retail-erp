package com.retailerp.hr;

import java.math.BigDecimal;
import java.util.UUID;

import com.retailerp.common.TenantScopedEntity;
import com.retailerp.hr.PayrollRules.Calculation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One employee's pay for one month (bulletin de paie). All figures are stored, computed by {@link PayrollRules}. */
@Entity
@Table(name = "payslips")
public class Payslip extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private PayrollRun run;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "employee_name", nullable = false, length = 150)
    private String employeeName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(length = 30)
    private String nss;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allowance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(name = "cnas_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal cnasBase;

    @Column(name = "cnas_employee", nullable = false, precision = 12, scale = 2)
    private BigDecimal cnasEmployee;

    @Column(name = "taxable_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableIncome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal irg;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "cnas_employer", nullable = false, precision = 12, scale = 2)
    private BigDecimal cnasEmployer;

    protected Payslip() {
    }

    /** Copies the employee's current details: a payslip doesn't change when the employee record does. */
    Payslip(PayrollRun run, Employee employee) {
        this.run = run;
        this.employeeId = employee.getId();
        this.employeeName = employee.getFullName();
        this.jobTitle = employee.getJobTitle();
        this.nss = employee.getNss();
        set(employee.getBaseSalary(), BigDecimal.ZERO, employee.getMonthlyAllowance());
    }

    /** Draft adjustments: a partial month (lower base), a bonus, different allowances. */
    void set(BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowance) {
        this.baseSalary = baseSalary;
        this.bonus = bonus;
        this.allowance = allowance;
        Calculation c = PayrollRules.calculate(baseSalary, bonus, allowance);
        this.gross = c.gross();
        this.cnasBase = c.cnasBase();
        this.cnasEmployee = c.cnasEmployee();
        this.taxableIncome = c.taxableIncome();
        this.irg = c.irg();
        this.netPay = c.netPay();
        this.cnasEmployer = c.cnasEmployer();
    }

    public PayrollRun getRun() {
        return run;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getNss() {
        return nss;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public BigDecimal getAllowance() {
        return allowance;
    }

    public BigDecimal getGross() {
        return gross;
    }

    public BigDecimal getCnasBase() {
        return cnasBase;
    }

    public BigDecimal getCnasEmployee() {
        return cnasEmployee;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getIrg() {
        return irg;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public BigDecimal getCnasEmployer() {
        return cnasEmployer;
    }
}
