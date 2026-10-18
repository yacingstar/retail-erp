package com.retailerp.returns;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.retailerp.catalog.Product;
import com.retailerp.common.TenantScopedEntity;
import com.retailerp.sales.SalesOrderLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "sales_return_lines")
public class SalesReturnLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_return_id", nullable = false)
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_line_id", nullable = false)
    private SalesOrderLine salesOrderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /** true = back on the shelf (a SALE_RETURN stock movement); false = damaged/thrown away (a loss). */
    @Column(nullable = false)
    private boolean restock;

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

    protected SalesReturnLine() {
    }

    SalesReturnLine(SalesOrderLine salesOrderLine, int quantity, boolean restock, BigDecimal lineSubtotal, BigDecimal lineDiscount,
            BigDecimal lineTax) {
        this.salesOrderLine = salesOrderLine;
        this.product = salesOrderLine.getProduct();
        this.quantity = quantity;
        this.restock = restock;
        this.unitCost = salesOrderLine.getUnitCost();
        this.lineSubtotal = lineSubtotal;
        this.lineDiscount = lineDiscount;
        this.lineTax = lineTax;
        this.lineTotal = lineSubtotal.subtract(lineDiscount).add(lineTax);
    }

    void attach(SalesReturn salesReturn) {
        this.salesReturn = salesReturn;
    }

    public SalesOrderLine getSalesOrderLine() {
        return salesOrderLine;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isRestock() {
        return restock;
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
