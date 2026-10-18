package com.retailerp.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PricingDtos {

    private PricingDtos() {
    }

    public record PriceListItemRequest(@NotNull UUID productId, @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) {
    }

    public record PriceListRequest(@NotBlank @Size(max = 100) String name, Boolean active,
            @Size(max = 5000) List<@Valid PriceListItemRequest> items) {
    }

    public record PriceListItemResponse(UUID productId, String sku, String productName, BigDecimal normalPrice, BigDecimal price) {
    }

    public record PriceListSummary(UUID id, String name, boolean active) {
    }

    public record PriceListResponse(UUID id, String name, boolean active, List<PriceListItemResponse> items) {
    }

    public record PromotionRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull PromotionType type,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal value,
            @Min(1) Integer buyQty,
            @Min(1) Integer getQty,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal minOrderAmount,
            PromotionScope scope,
            @Size(max = 100) String category,
            UUID productId,
            @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "letters, digits, - and _ only") String couponCode,
            LocalDate startsOn,
            LocalDate endsOn,
            Boolean active) {
    }

    public record PromotionResponse(UUID id, String name, PromotionType type, BigDecimal value, Integer buyQty, Integer getQty,
            BigDecimal minOrderAmount, PromotionScope scope, String category, UUID productId, String productName, String couponCode,
            LocalDate startsOn, LocalDate endsOn, boolean active, boolean runningToday) {
    }
}
