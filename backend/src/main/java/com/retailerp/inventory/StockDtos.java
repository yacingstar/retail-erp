package com.retailerp.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class StockDtos {

    private StockDtos() {
    }

    public record StockLevel(UUID productId, String sku, String productName, String unit, UUID warehouseId, String warehouseCode,
            String warehouseName, long quantity, int reorderLevel) {
    }

    public record LowStock(UUID productId, String sku, String productName, String unit, long onHand, int reorderLevel) {
    }

    public record MovementResponse(UUID id, UUID productId, String sku, String productName, UUID warehouseId, String warehouseCode,
            int quantity, MovementType type, String referenceType, UUID referenceId, String note, UUID createdBy, Instant createdAt) {

        static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getSku(), m.getProduct().getName(),
                    m.getWarehouse().getId(), m.getWarehouse().getCode(), m.getQuantity(), m.getType(), m.getReferenceType(),
                    m.getReferenceId(), m.getNote(), m.getCreatedBy(), m.getCreatedAt());
        }
    }

    /** Positive quantity adds stock (found, opening stock), negative removes it (damaged, lost). */
    public record AdjustmentRequest(@NotNull UUID productId, @NotNull UUID warehouseId, @NotNull Integer quantity,
            @NotBlank @Size(max = 500) String note) {
    }

    public record TransferRequest(@NotNull UUID productId, @NotNull UUID fromWarehouseId, @NotNull UUID toWarehouseId,
            @NotNull @Min(1) Integer quantity, @Size(max = 500) String note) {
    }
}
