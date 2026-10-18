package com.retailerp.purchasing;

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

/**
 * An aggregate: the order and its lines are always loaded/saved together through the order.
 * {@code cascade = ALL} + {@code orphanRemoval}: saving the order saves its lines; removing a line from the list
 * deletes it. Status changes go through methods that enforce the allowed transitions.
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends AuditedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String number, UUID createdBy) {
        this.number = number;
        this.createdBy = createdBy;
    }

    public void edit(Supplier supplier, Warehouse warehouse, LocalDate orderDate, LocalDate expectedDate, String notes,
            List<PurchaseOrderLine> newLines) {
        requireStatus(PurchaseOrderStatus.DRAFT, "edit");
        this.supplier = supplier;
        this.warehouse = warehouse;
        this.orderDate = orderDate;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.lines.clear();
        int lineNo = 1;
        BigDecimal sum = BigDecimal.ZERO;
        for (PurchaseOrderLine line : newLines) {
            line.attach(this, lineNo++);
            this.lines.add(line);
            sum = sum.add(line.getLineTotal());
        }
        this.total = sum;
    }

    public void markOrdered() {
        requireStatus(PurchaseOrderStatus.DRAFT, "order");
        status = PurchaseOrderStatus.ORDERED;
    }

    public void markReceived() {
        requireStatus(PurchaseOrderStatus.ORDERED, "receive");
        status = PurchaseOrderStatus.RECEIVED;
        receivedAt = Instant.now();
    }

    public void cancel() {
        if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.ORDERED) {
            throw new BusinessRuleException("Only DRAFT or ORDERED purchase orders can be cancelled (this one is " + status + ").");
        }
        status = PurchaseOrderStatus.CANCELLED;
    }

    public void requireStatus(PurchaseOrderStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException("Can't " + action + " purchase order " + number + ": it is " + status + ", not " + expected + ".");
        }
    }

    public String getNumber() {
        return number;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}
