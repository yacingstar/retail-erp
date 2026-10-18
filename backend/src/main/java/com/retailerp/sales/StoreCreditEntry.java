package com.retailerp.sales;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.retailerp.common.TenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One change to a customer's store credit: + given (a refund), − spent (a payment). Append-only, like stock. */
@Entity
@Immutable
@Table(name = "store_credit_entries")
public class StoreCreditEntry extends TenantOwnedEntity {

    public enum Reason {
        RETURN_REFUND, PAYMENT
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(length = 200)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    protected StoreCreditEntry() {
    }

    StoreCreditEntry(Customer customer, BigDecimal amount, Reason reason, UUID referenceId, String note, UUID createdBy) {
        this.customer = customer;
        this.amount = amount;
        this.reason = reason;
        this.referenceId = referenceId;
        this.note = note;
        this.createdBy = createdBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Reason getReason() {
        return reason;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getNote() {
        return note;
    }
}
