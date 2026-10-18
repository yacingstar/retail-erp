package com.retailerp.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.retailerp.sales.SalesDtos.LineResponse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class InvoicingDtos {

    private InvoicingDtos() {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 100) String reference,
            Instant paidAt) {
    }

    public record PaymentResponse(UUID id, BigDecimal amount, PaymentMethod method, String reference, Instant paidAt, UUID createdBy) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getAmount(), p.getMethod(), p.getReference(), p.getPaidAt(), p.getCreatedBy());
        }
    }

    public record InvoiceSummary(UUID id, String number, UUID salesOrderId, String salesOrderNumber, UUID customerId, String customerName,
            InvoiceStatus status, LocalDate issueDate, LocalDate dueDate, BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue) {

        static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getNumber(), i.getSalesOrder().getId(), i.getSalesOrder().getNumber(),
                    i.getCustomer() == null ? null : i.getCustomer().getId(), i.getCustomer() == null ? null : i.getCustomer().getName(),
                    i.getStatus(), i.getIssueDate(), i.getDueDate(), i.getTotal(), i.getAmountPaid(), i.balanceDue());
        }
    }

    public record InvoiceResponse(UUID id, String number, UUID salesOrderId, String salesOrderNumber, UUID customerId, String customerName,
            String customerEmail, String customerAddress, InvoiceStatus status, LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total, BigDecimal amountPaid, BigDecimal amountCredited,
            BigDecimal amountRefunded, BigDecimal balanceDue, List<LineResponse> lines,
            List<PaymentResponse> payments) {
    }
}
