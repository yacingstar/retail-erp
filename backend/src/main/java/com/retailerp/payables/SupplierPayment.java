package com.retailerp.payables;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.retailerp.common.TenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "supplier_payments")
public class SupplierPayment extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private SupplierBill bill;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierPaymentMethod method;

    @Column(length = 100)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected SupplierPayment() {
    }

    SupplierPayment(SupplierBill bill, BigDecimal amount, SupplierPaymentMethod method, String reference, Instant paidAt, UUID createdBy) {
        this.bill = bill;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.paidAt = paidAt;
        this.createdBy = createdBy;
    }

    public SupplierBill getBill() {
        return bill;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SupplierPaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
