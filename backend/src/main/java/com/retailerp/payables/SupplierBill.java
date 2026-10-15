package com.retailerp.payables;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.retailerp.common.AuditedEntity;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.purchasing.PurchaseOrder;
import com.retailerp.purchasing.Supplier;

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

/** A bill we received from a supplier: what we owe them. */
@Entity
@Table(name = "supplier_bills")
public class SupplierBill extends AuditedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    /** The supplier's own invoice number, as printed on their paper bill. */
    @Column(name = "supplier_reference", length = 100)
    private String supplierReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", updatable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<SupplierBillLine> lines = new ArrayList<>();

    protected SupplierBill() {
    }

    SupplierBill(String number, PurchaseOrder purchaseOrder, UUID createdBy) {
        this.number = number;
        this.purchaseOrder = purchaseOrder;
        this.createdBy = createdBy;
    }

    void edit(Supplier supplier, String supplierReference, LocalDate billDate, LocalDate dueDate, String description,
            List<SupplierBillLine> newLines) {
        if (status != BillStatus.DRAFT) {
            throw new BusinessRuleException("Bill " + number + " is " + status + "; only drafts can be edited.");
        }
        this.supplier = supplier;
        this.supplierReference = supplierReference;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.description = description;
        this.lines.clear();
        int lineNo = 1;
        BigDecimal sum = BigDecimal.ZERO;
        for (SupplierBillLine line : newLines) {
            line.attach(this, lineNo++);
            lines.add(line);
            sum = sum.add(line.getLineTotal());
        }
        this.total = sum;
    }

    void approve() {
        if (status != BillStatus.DRAFT) {
            throw new BusinessRuleException("Bill " + number + " is already " + status + ".");
        }
        if (total.signum() == 0) {
            throw new BusinessRuleException("A bill must have a total above zero.");
        }
        status = BillStatus.OPEN;
        approvedAt = Instant.now();
    }

    void registerPayment(BigDecimal amount) {
        if (status != BillStatus.OPEN && status != BillStatus.PARTIALLY_PAID) {
            throw new BusinessRuleException("Bill " + number + " is " + status + "; payments are recorded on approved, unpaid bills.");
        }
        if (amount.compareTo(balanceDue()) > 0) {
            throw new BusinessRuleException("Payment " + amount + " is more than the " + balanceDue() + " still owed on " + number + ".");
        }
        amountPaid = amountPaid.add(amount);
        status = balanceDue().signum() == 0 ? BillStatus.PAID : BillStatus.PARTIALLY_PAID;
    }

    void voidBill() {
        if (amountPaid.signum() > 0) {
            throw new BusinessRuleException("Bill " + number + " has payments; it can't be voided.");
        }
        if (status == BillStatus.VOID) {
            throw new BusinessRuleException("Bill " + number + " is already void.");
        }
        status = BillStatus.VOID;
    }

    public BigDecimal balanceDue() {
        return total.subtract(amountPaid);
    }

    public String getNumber() {
        return number;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public BillStatus getStatus() {
        return status;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public List<SupplierBillLine> getLines() {
        return lines;
    }
}
