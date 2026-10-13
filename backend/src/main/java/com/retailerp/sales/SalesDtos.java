package com.retailerp.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.retailerp.inventory.StockDtos.MovementResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record LineRequest(
            @NotNull UUID productId,
            @NotNull @Min(1) Integer quantity,
            /** Optional: defaults to the product's sale price. */
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    public record SalesOrderRequest(
            UUID customerId,
            @NotNull UUID warehouseId,
            LocalDate orderDate,
            @Size(max = 5000) String notes,
            /** Optional % off the whole order (cashiers are limited, see SalesService). */
            @DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @NotEmpty @Size(max = 500) List<@Valid LineRequest> lines) {
    }

    public record LineResponse(int lineNo, UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
            BigDecimal taxRate, BigDecimal lineSubtotal, BigDecimal lineDiscount, BigDecimal lineTax, BigDecimal lineTotal) {

        public static LineResponse from(SalesOrderLine l) {
            return new LineResponse(l.getLineNo(), l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
                    l.getQuantity(), l.getUnitPrice(), l.getTaxRate(), l.getLineSubtotal(), l.getLineDiscount(), l.getLineTax(),
                    l.getLineTotal());
        }
    }

    public record SalesOrderSummary(UUID id, String number, UUID customerId, String customerName, UUID warehouseId,
            String warehouseCode, SalesOrderStatus status, LocalDate orderDate, BigDecimal total) {

        static SalesOrderSummary from(SalesOrder so) {
            return new SalesOrderSummary(so.getId(), so.getNumber(), so.getCustomer() == null ? null : so.getCustomer().getId(),
                    so.getCustomer() == null ? null : so.getCustomer().getName(), so.getWarehouse().getId(), so.getWarehouse().getCode(),
                    so.getStatus(), so.getOrderDate(), so.getTotal());
        }
    }

    public record SalesOrderResponse(UUID id, String number, UUID customerId, String customerName, UUID warehouseId,
            String warehouseCode, String warehouseName, SalesOrderStatus status, LocalDate orderDate, Instant confirmedAt, String notes,
            BigDecimal subtotal, BigDecimal discountPercent, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total, List<LineResponse> lines, UUID invoiceId, String invoiceNumber,
            List<MovementResponse> movements, Instant createdAt, Instant updatedAt) {
    }

    public record DailySales(LocalDate date, BigDecimal total, long orders) {
    }
}
