package com.retailerp.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.retailerp.catalog.Product;
import com.retailerp.common.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotions")
public class Promotion extends AuditedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "buy_qty")
    private Integer buyQty;

    @Column(name = "get_qty")
    private Integer getQty;

    @Column(name = "min_order_amount", precision = 14, scale = 2)
    private BigDecimal minOrderAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PromotionScope scope = PromotionScope.ALL;

    @Column(length = 100)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** null = automatic; otherwise only applies when this code is entered. */
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean active = true;

    protected Promotion() {
    }

    Promotion(PromotionType type) {
        this.type = type;
    }

    void update(String name, PromotionType type, BigDecimal value, Integer buyQty, Integer getQty, BigDecimal minOrderAmount,
            PromotionScope scope, String category, Product product, String couponCode, LocalDate startsOn, LocalDate endsOn, boolean active) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.buyQty = buyQty;
        this.getQty = getQty;
        this.minOrderAmount = minOrderAmount;
        this.scope = scope;
        this.category = category;
        this.product = product;
        this.couponCode = couponCode;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.active = active;
    }

    /** Valid on that date (active and inside the optional date range). */
    public boolean runsOn(LocalDate date) {
        return active && (startsOn == null || !date.isBefore(startsOn)) && (endsOn == null || !date.isAfter(endsOn));
    }

    public boolean appliesTo(Product p) {
        return switch (scope) {
            case ALL -> true;
            case CATEGORY -> category != null && p.getCategory() != null && category.equalsIgnoreCase(p.getCategory());
            case PRODUCT -> product != null && product.getId().equals(p.getId());
        };
    }

    public String getName() {
        return name;
    }

    public PromotionType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Integer getBuyQty() {
        return buyQty;
    }

    public Integer getGetQty() {
        return getQty;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public PromotionScope getScope() {
        return scope;
    }

    public String getCategory() {
        return category;
    }

    public Product getProduct() {
        return product;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public boolean isActive() {
        return active;
    }
}
