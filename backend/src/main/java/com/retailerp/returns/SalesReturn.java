package com.retailerp.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.retailerp.catalog.Warehouse;
import com.retailerp.common.TenantOwnedEntity;
import com.retailerp.invoicing.Invoice;
import com.retailerp.invoicing.PaymentMethod;
import com.retailerp.sales.Customer;
import com.retailerp.sales.SalesOrder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A return of goods from a confirmed sale. The document IS the credit note (CN-000001): it reduces what the invoice
 * is worth and, if the customer had already paid, says how they got their money back. Immutable once created.
 */
@Entity
@Immutable
@Table(name = "sales_returns")
public class SalesReturn extends TenantOwnedEntity {

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", length = 20)
    private PaymentMethod refundMethod;

    @Column(name = "pos_shift_id")
    private UUID posShiftId;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<SalesReturnLine> lines = new ArrayList<>();

    protected SalesReturn() {
    }

    SalesReturn(String number, SalesOrder order, Invoice invoice, String reason, UUID createdBy) {
        this.number = number;
        this.returnDate = LocalDate.now();
        this.salesOrder = order;
        this.invoice = invoice;
        this.customer = order.getCustomer();
        this.warehouse = order.getWarehouse();
        this.reason = reason;
        this.createdBy = createdBy;
    }

    /** Called while building the document, before it's saved (an @Immutable entity is never updated afterwards). */
    void addLine(SalesReturnLine line) {
        line.attach(this);
        lines.add(line);
        subtotal = subtotal.add(line.getLineSubtotal());
        discountTotal = discountTotal.add(line.getLineDiscount());
        taxTotal = taxTotal.add(line.getLineTax());
        total = total.add(line.getLineTotal());
    }

    void refund(BigDecimal amount, PaymentMethod method, UUID posShiftId) {
        this.refundAmount = amount;
        this.refundMethod = method;
        this.posShiftId = posShiftId;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public SalesOrder getSalesOrder() {
        return salesOrder;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public PaymentMethod getRefundMethod() {
        return refundMethod;
    }

    public UUID getPosShiftId() {
        return posShiftId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<SalesReturnLine> getLines() {
        return lines;
    }
}
