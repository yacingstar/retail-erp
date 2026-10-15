package com.retailerp.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.retailerp.catalog.Warehouse;
import com.retailerp.common.AuditedEntity;
import com.retailerp.common.BusinessRuleException;

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

@Entity
@Table(name = "sales_orders")
public class SalesOrder extends AuditedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    /** Null = walk-in customer (anonymous counter sale). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Set for sales made at the Point of Sale (plain id, so the sales module doesn't depend on the pos module). */
    @Column(name = "pos_shift_id", updatable = false)
    private UUID posShiftId;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SalesOrderLine> lines = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(String number, UUID createdBy, UUID posShiftId) {
        this.number = number;
        this.createdBy = createdBy;
        this.posShiftId = posShiftId;
    }

    /** Lines must already carry {@code discountPercent} (see SalesOrderLine). */
    public void edit(Customer customer, Warehouse warehouse, LocalDate orderDate, String notes, BigDecimal discountPercent,
            String couponCode, List<SalesOrderLine> newLines) {
        requireStatus(SalesOrderStatus.DRAFT, "edit");
        this.couponCode = couponCode;
        this.customer = customer;
        this.warehouse = warehouse;
        this.orderDate = orderDate;
        this.notes = notes;
        this.discountPercent = discountPercent;
        this.lines.clear();
        int lineNo = 1;
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SalesOrderLine line : newLines) {
            line.attach(this, lineNo++);
            this.lines.add(line);
            sub = sub.add(line.getLineSubtotal());
            discount = discount.add(line.getLineDiscount());
            tax = tax.add(line.getLineTax());
        }
        this.subtotal = sub;
        this.discountTotal = discount;
        this.taxTotal = tax;
        this.total = sub.subtract(discount).add(tax);
    }

    public void confirm() {
        requireStatus(SalesOrderStatus.DRAFT, "confirm");
        status = SalesOrderStatus.CONFIRMED;
        confirmedAt = Instant.now();
    }

    public void cancel() {
        if (status == SalesOrderStatus.CANCELLED) {
            throw new BusinessRuleException("Sales order " + number + " is already cancelled.");
        }
        status = SalesOrderStatus.CANCELLED;
    }

    public void requireStatus(SalesOrderStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException("Can't " + action + " sales order " + number + ": it is " + status + ", not " + expected + ".");
        }
    }

    public String getNumber() {
        return number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public UUID getPosShiftId() {
        return posShiftId;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<SalesOrderLine> getLines() {
        return lines;
    }
}
