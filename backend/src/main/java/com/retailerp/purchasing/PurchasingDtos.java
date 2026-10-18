package com.retailerp.purchasing;

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

public final class PurchasingDtos {

    private PurchasingDtos() {
    }

    public record LineRequest(
            @NotNull UUID productId,
            @NotNull @Min(1) Integer quantity,
            /** Optional: defaults to the product's current cost price. */
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }

    /** {@code @Valid} on the list makes Bean Validation also check every line. */
    public record PurchaseOrderRequest(
            @NotNull UUID supplierId,
            @NotNull UUID warehouseId,
            LocalDate orderDate,
            LocalDate expectedDate,
            @Size(max = 5000) String notes,
            @NotEmpty @Size(max = 500) List<@Valid LineRequest> lines) {
    }

    public record LineResponse(int lineNo, UUID productId, String sku, String productName, int quantity, BigDecimal unitCost,
            BigDecimal lineTotal) {
    }

    public record PurchaseOrderSummary(UUID id, String number, UUID supplierId, String supplierName, UUID warehouseId,
            String warehouseCode, PurchaseOrderStatus status, LocalDate orderDate, LocalDate expectedDate, BigDecimal total) {

        static PurchaseOrderSummary from(PurchaseOrder po) {
            return new PurchaseOrderSummary(po.getId(), po.getNumber(), po.getSupplier().getId(), po.getSupplier().getName(),
                    po.getWarehouse().getId(), po.getWarehouse().getCode(), po.getStatus(), po.getOrderDate(), po.getExpectedDate(),
                    po.getTotal());
        }
    }

    public record PurchaseOrderResponse(UUID id, String number, UUID supplierId, String supplierName, UUID warehouseId,
            String warehouseCode, String warehouseName, PurchaseOrderStatus status, LocalDate orderDate, LocalDate expectedDate,
            Instant receivedAt, String notes, BigDecimal total, List<LineResponse> lines, List<MovementResponse> movements,
            Instant createdAt, Instant updatedAt) {
    }
}
