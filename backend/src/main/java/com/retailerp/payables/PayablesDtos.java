package com.retailerp.payables;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PayablesDtos {

    private PayablesDtos() {
    }

    public record BillLineRequest(
            @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    }

    public record BillRequest(
            @NotNull UUID supplierId,
            @Size(max = 100) String supplierReference,
            LocalDate billDate,
            LocalDate dueDate,
            @Size(max = 5000) String description,
            /** Manual bills only: expense account code (default 607). */
            @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{2,10}$") String expenseAccount,
            @NotEmpty @Size(max = 500) List<@Valid BillLineRequest> lines) {
    }

    public record SupplierPaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull SupplierPaymentMethod method,
            @Size(max = 100) String reference,
            Instant paidAt) {
    }

    public record BillLineResponse(int lineNo, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal,
            BigDecimal taxRate, BigDecimal lineTax) {
    }

    public record SupplierPaymentResponse(UUID id, BigDecimal amount, SupplierPaymentMethod method, String reference, Instant paidAt) {
    }

    public record BillSummary(UUID id, String number, String supplierReference, UUID supplierId, String supplierName, BillStatus status,
            LocalDate billDate, LocalDate dueDate, BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue, String purchaseOrderNumber) {
    }

    public record BillResponse(UUID id, String number, String supplierReference, UUID supplierId, String supplierName, BillStatus status,
            LocalDate billDate, LocalDate dueDate, String description, String expenseAccount, BigDecimal subtotal, BigDecimal taxTotal,
            BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue,
            UUID purchaseOrderId, String purchaseOrderNumber, Instant approvedAt, List<BillLineResponse> lines,
            List<SupplierPaymentResponse> payments) {
    }

    /** One row of a supplier statement: a bill (+ we owe) or a payment (− we paid), with the running balance. */
    public record StatementEntry(LocalDate date, String type, UUID billId, String document, String reference, BigDecimal amount,
            BigDecimal balance) {
    }

    public record SupplierStatement(UUID supplierId, String supplierName, BigDecimal totalBilled, BigDecimal totalPaid, BigDecimal balance,
            List<StatementEntry> entries) {
    }
}
