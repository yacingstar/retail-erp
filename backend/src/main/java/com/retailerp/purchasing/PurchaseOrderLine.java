package com.retailerp.purchasing;

import java.math.BigDecimal;

import com.retailerp.catalog.Product;
import com.retailerp.common.Money;
import com.retailerp.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseOrderLine() {
    }

    public PurchaseOrderLine(Product product, int quantity, BigDecimal unitCost) {
        this.product = product;
        this.quantity = quantity;
        this.unitCost = Money.round(unitCost);
        this.lineTotal = Money.times(this.unitCost, quantity);
    }

    void attach(PurchaseOrder order, int lineNo) {
        this.purchaseOrder = order;
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
