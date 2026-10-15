package com.retailerp.pricing;

import java.math.BigDecimal;

import com.retailerp.catalog.Product;
import com.retailerp.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_list_items")
public class PriceListItem extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    protected PriceListItem() {
    }

    PriceListItem(Product product, BigDecimal price) {
        this.product = product;
        this.price = price;
    }

    void attach(PriceList priceList) {
        this.priceList = priceList;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
