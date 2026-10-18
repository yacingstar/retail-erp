package com.retailerp.hr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class HrDtos {

    private HrDtos() {
    }

    public record EmployeeRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 100) String jobTitle,
            @NotNull ContractType contractType,
            @NotNull LocalDate hireDate,
            LocalDate endDate,
            @Size(max = 30) String nin,
            @Size(max = 30) String nss,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseSalary,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal monthlyAllowance,
            @Size(max = 40) String phone,
            @Size(max = 40) String bankAccount,
            String notes,
            Boolean active) {
    }

    public record EmployeeResponse(UUID id, String fullName, String jobTitle, ContractType contractType, LocalDate hireDate, LocalDate endDate,
            String nin, String nss, BigDecimal baseSalary, BigDecimal monthlyAllowance, String phone, String bankAccount, String notes,
            boolean active, Instant createdAt) {
    }

    /** What the employee's pay would be with the current figures: shown live in the employee form. */
    public record PayPreview(BigDecimal gross, BigDecimal cnasEmployee, BigDecimal taxableIncome, BigDecimal irg, BigDecimal netPay,
            BigDecimal cnasEmployer, BigDecimal employerCost) {
    }

    public record PayrollRunRequest(@Min(2000) @Max(2100) int year, @Min(1) @Max(12) int month) {
    }

    public record PayslipRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseSalary,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal bonus,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal allowance) {
    }

    public record PayRequest(@NotNull SalaryPaymentMethod method) {
    }

    public record PayslipResponse(UUID id, UUID employeeId, String employeeName, String jobTitle, String nss, BigDecimal baseSalary,
            BigDecimal bonus, BigDecimal allowance, BigDecimal gross, BigDecimal cnasBase, BigDecimal cnasEmployee, BigDecimal taxableIncome,
            BigDecimal irg, BigDecimal netPay, BigDecimal cnasEmployer) {
    }

    public record PayrollRunSummary(UUID id, String number, int year, int month, PayrollStatus status, int employees, BigDecimal totalGross,
            BigDecimal totalNet, BigDecimal totalCnasEmployer, Instant approvedAt, Instant paidAt) {
    }

    public record PayrollRunResponse(UUID id, String number, int year, int month, LocalDate periodEnd, PayrollStatus status,
            SalaryPaymentMethod paymentMethod, BigDecimal totalGross, BigDecimal totalCnasEmployee, BigDecimal totalIrg, BigDecimal totalNet,
            BigDecimal totalCnasEmployer, BigDecimal employerCost, Instant approvedAt, Instant paidAt, List<PayslipResponse> payslips) {
    }

    /** A payslip in an employee's history. */
    public record EmployeePayslip(UUID runId, String runNumber, int year, int month, PayrollStatus status, BigDecimal gross, BigDecimal netPay) {
    }
}
