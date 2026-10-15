package com.retailerp.inventory;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.retailerp.catalog.Product;
import com.retailerp.catalog.Warehouse;
import com.retailerp.common.TenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One change of stock. Append-only: {@code @Immutable} makes Hibernate never issue UPDATEs for it, there are no
 * setters, and the database rejects UPDATE/DELETE (no privilege + trigger). Mistakes are fixed with a new,
 * correcting movement, which also leaves a full audit trail.
 * <p>
 * {@code FetchType.LAZY}: product/warehouse are only loaded from the DB if we actually access them.
 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType type;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    protected StockMovement() {
    }

    StockMovement(Product product, Warehouse warehouse, int quantity, MovementType type, String referenceType, UUID referenceId,
            String note, UUID createdBy) {
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public int getQuantity() {
        return quantity;
    }

    public MovementType getType() {
        return type;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getNote() {
        return note;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
