package com.retailerp.pos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.retailerp.catalog.Warehouse;
import com.retailerp.common.AuditedEntity;
import com.retailerp.common.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A cashier's working session at a store. Opened with a cash float; closed with a cash count. The difference
 * between the counted and the expected cash (float + cash taken) shows mistakes or theft.
 */
@Entity
@Table(name = "pos_shifts")
public class PosShift extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "opening_cash", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal openingCash;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_cash", precision = 14, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 14, scale = 2)
    private BigDecimal countedCash;

    @Column(columnDefinition = "text")
    private String notes;

    protected PosShift() {
    }

    public PosShift(Warehouse warehouse, UUID openedBy, BigDecimal openingCash) {
        this.warehouse = warehouse;
        this.openedBy = openedBy;
        this.openingCash = openingCash;
        this.openedAt = Instant.now();
    }

    public void close(UUID closedBy, BigDecimal expectedCash, BigDecimal countedCash, String notes) {
        if (status != ShiftStatus.OPEN) {
            throw new BusinessRuleException("This shift is already closed.");
        }
        this.status = ShiftStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = Instant.now();
        this.expectedCash = expectedCash;
        this.countedCash = countedCash;
        this.notes = notes;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public ShiftStatus getStatus() {
        return status;
    }

    public UUID getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public BigDecimal getOpeningCash() {
        return openingCash;
    }

    public UUID getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public BigDecimal getExpectedCash() {
        return expectedCash;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public String getNotes() {
        return notes;
    }
}
