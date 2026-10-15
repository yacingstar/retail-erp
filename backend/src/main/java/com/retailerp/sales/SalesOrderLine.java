package com.retailerp.sales;

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

/**
 * Prices and tax rate are COPIED from the product when the line is created: later price changes don't alter old orders.
 * Money per line: lineSubtotal (price × qty) − lineDiscount = net; lineTax = net × taxRate; lineTotal = net + lineTax.
 */
@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    /** The product's cost price when the line was created (for profit reports). */
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "line_discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineDiscount;

    @Column(name = "line_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTax;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected SalesOrderLine() {
    }

    /** @param discountPercent the order-level discount, applied to every line (0 = none) */
    public SalesOrderLine(Product product, int quantity, BigDecimal unitPrice, BigDecimal taxRate, BigDecimal discountPercent) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = Money.round(unitPrice);
        this.taxRate = taxRate;
        this.unitCost = product.getCostPrice();
        this.lineSubtotal = Money.times(this.unitPrice, quantity);
        this.lineDiscount = Money.percentOf(lineSubtotal, discountPercent);
        BigDecimal net = lineSubtotal.subtract(lineDiscount);
        this.lineTax = Money.percentOf(net, taxRate);
        this.lineTotal = net.add(lineTax);
    }

    void attach(SalesOrder order, int lineNo) {
        this.salesOrder = order;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getLineDiscount() {
        return lineDiscount;
    }

    public BigDecimal getLineTax() {
        return lineTax;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
