package com.retailerp.catalog;

import java.math.BigDecimal;

import com.retailerp.common.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A sellable item. Note: no "quantity" field. Stock lives in stock_movements (see StockService). */
@Entity
@Table(name = "products")
public class Product extends AuditedEntity {

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String sku) {
        this.sku = sku;
    }

    public void update(String sku, String name, String description, String unit, BigDecimal salePrice, BigDecimal costPrice,
            BigDecimal taxRate, int reorderLevel, boolean active) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.salePrice = salePrice;
        this.costPrice = costPrice;
        this.taxRate = taxRate;
        this.reorderLevel = reorderLevel;
        this.active = active;
    }

    /** Called when goods are received: the last purchase cost becomes the product's cost. */
    public void updateCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public boolean isActive() {
        return active;
    }
}
