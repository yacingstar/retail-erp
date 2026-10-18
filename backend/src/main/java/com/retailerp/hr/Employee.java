package com.retailerp.hr;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.retailerp.common.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A person on the payroll. Employees don't log in: they are records managed by admins and managers. */
@Entity
@Table(name = "employees")
public class Employee extends AuditedEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 30)
    private String nin;

    @Column(length = 30)
    private String nss;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "monthly_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyAllowance = BigDecimal.ZERO;

    @Column(length = 40)
    private String phone;

    @Column(name = "bank_account", length = 40)
    private String bankAccount;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Employee() {
    }

    Employee(String fullName) {
        this.fullName = fullName;
    }

    void update(String fullName, String jobTitle, ContractType contractType, LocalDate hireDate, LocalDate endDate, String nin, String nss,
            BigDecimal baseSalary, BigDecimal monthlyAllowance, String phone, String bankAccount, String notes, boolean active) {
        this.fullName = fullName;
        this.jobTitle = jobTitle;
        this.contractType = contractType;
        this.hireDate = hireDate;
        this.endDate = endDate;
        this.nin = nin;
        this.nss = nss;
        this.baseSalary = baseSalary;
        this.monthlyAllowance = monthlyAllowance;
        this.phone = phone;
        this.bankAccount = bankAccount;
        this.notes = notes;
        this.active = active;
    }

    /** On the payroll for a month: active, hired by the month's end, and not gone before it started. */
    boolean worksDuring(LocalDate monthStart, LocalDate monthEnd) {
        return active && !hireDate.isAfter(monthEnd) && (endDate == null || !endDate.isBefore(monthStart));
    }

    public String getFullName() {
        return fullName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNin() {
        return nin;
    }

    public String getNss() {
        return nss;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public BigDecimal getMonthlyAllowance() {
        return monthlyAllowance;
    }

    public String getPhone() {
        return phone;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
