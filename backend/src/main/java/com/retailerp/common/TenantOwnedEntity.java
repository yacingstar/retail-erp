package com.retailerp.common;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base class for every table that belongs to a tenant.
 * <p>
 * {@code @MappedSuperclass}: not a table itself; its fields become columns of each subclass's table.
 * {@code @TenantId}: Hibernate sets this from {@link com.retailerp.tenancy.TenantIdentifierResolver} on insert and
 * adds {@code tenant_id = ?} to every query. It's never set by our code.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
