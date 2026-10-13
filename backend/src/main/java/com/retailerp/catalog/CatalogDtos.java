package com.retailerp.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @Size(max = 20) String unit,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            @Min(0) Integer reorderLevel,
            Boolean active) {
    }

    public record ProductResponse(UUID id, String sku, String name, String description, String unit, BigDecimal salePrice,
            BigDecimal costPrice, BigDecimal taxRate, int reorderLevel, boolean active, Instant createdAt, Instant updatedAt) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getUnit(), p.getSalePrice(),
                    p.getCostPrice(), p.getTaxRate(), p.getReorderLevel(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record WarehouseRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String address,
            Boolean active) {
    }

    public record WarehouseResponse(UUID id, String code, String name, String address, boolean active) {

        public static WarehouseResponse from(Warehouse w) {
            return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getAddress(), w.isActive());
        }
    }
}
