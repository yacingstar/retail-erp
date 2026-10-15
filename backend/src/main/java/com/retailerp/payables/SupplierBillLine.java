package com.retailerp.payables;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.retailerp.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Free-text lines: a bill can be for goods ("Mug × 10") or anything else ("Rent, October"). */
@Entity
@Table(name = "supplier_bill_lines")
public class SupplierBillLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private SupplierBill bill;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected SupplierBillLine() {
    }

    SupplierBillLine(String description, BigDecimal quantity, BigDecimal unitPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    void attach(SupplierBill bill, int lineNo) {
        this.bill = bill;
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
