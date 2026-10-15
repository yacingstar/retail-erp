package com.retailerp.pos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.retailerp.invoicing.PaymentMethod;
import com.retailerp.sales.SalesDtos.LineRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PosDtos {

    private PosDtos() {
    }

    public record OpenShiftRequest(@NotNull UUID warehouseId,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal openingCash) {
    }

    public record CloseShiftRequest(@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal countedCash,
            @Size(max = 2000) String notes) {
    }

    public record SalesTotals(long count, BigDecimal total) {
    }

    public record MethodTotal(PaymentMethod method, BigDecimal amount) {
    }

    public record ShiftResponse(UUID id, ShiftStatus status, UUID warehouseId, String warehouseCode, String warehouseName, UUID openedBy,
            String openedByName, Instant openedAt, BigDecimal openingCash, UUID closedBy, Instant closedAt, long salesCount, BigDecimal salesTotal,
            List<MethodTotal> payments, BigDecimal cashRefunds, BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference, String notes) {
    }

    /** One POS sale: what's in the cart, how it's paid. */
    public record CheckoutRequest(
            UUID customerId,
            @NotEmpty @Size(max = 500) List<@Valid LineRequest> lines,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @NotNull PaymentMethod paymentMethod,
            /** Cash handed over by the customer (CASH only); the change is computed from it. */
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal cashReceived) {
    }

    /** Amount before the order discount and tax (those are shown once, at the bottom of the receipt). */
    public record ReceiptLine(String name, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record Receipt(UUID salesOrderId, String orderNumber, UUID invoiceId, String invoiceNumber, String shopName,
            String warehouseName, String cashierName, String customerName, Instant soldAt, List<ReceiptLine> lines, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total, PaymentMethod paymentMethod, BigDecimal cashReceived,
            BigDecimal change) {
    }
}
