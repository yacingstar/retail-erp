package com.retailerp.returns;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.retailerp.invoicing.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record Returned(UUID salesOrderLineId, long quantity, BigDecimal subtotal, BigDecimal discount, BigDecimal tax) {
    }

    public record ReturnLineRequest(@NotNull UUID salesOrderLineId, @NotNull @Min(1) Integer quantity, boolean restock) {
    }

    /** refundMethod is only needed when money is due back (the invoice was already paid). */
    public record ReturnRequest(
            @NotNull UUID salesOrderId,
            @NotEmpty @Size(max = 500) List<@Valid ReturnLineRequest> lines,
            PaymentMethod refundMethod,
            @Size(max = 2000) String reason) {
    }

    /** One line of the original sale, with how much can still be returned. */
    public record ReturnableLine(UUID salesOrderLineId, int lineNo, UUID productId, String sku, String productName, int sold, long returned,
            long returnable, BigDecimal unitPrice, BigDecimal unitRefund) {
    }

    public record Returnable(UUID salesOrderId, String salesOrderNumber, UUID customerId, String customerName, String invoiceNumber,
            BigDecimal invoiceTotal, BigDecimal amountPaid, List<ReturnableLine> lines) {
    }

    public record ReturnLineResponse(UUID productId, String sku, String productName, int quantity, boolean restock, BigDecimal lineSubtotal,
            BigDecimal lineDiscount, BigDecimal lineTax, BigDecimal lineTotal) {
    }

    public record ReturnSummary(UUID id, String number, LocalDate returnDate, UUID salesOrderId, String salesOrderNumber, String customerName,
            BigDecimal total, BigDecimal refundAmount, PaymentMethod refundMethod) {
    }

    public record ReturnResponse(UUID id, String number, LocalDate returnDate, UUID salesOrderId, String salesOrderNumber, UUID invoiceId,
            String invoiceNumber, UUID customerId, String customerName, String warehouseCode, String reason, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total, BigDecimal refundAmount, PaymentMethod refundMethod,
            List<ReturnLineResponse> lines, Instant createdAt) {
    }
}
