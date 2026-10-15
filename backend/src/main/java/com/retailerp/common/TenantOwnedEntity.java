package com.retailerp.common;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** A tenant-scoped entity with a created_at timestamp (filled in by Hibernate on insert). */
@MappedSuperclass
public abstract class TenantOwnedEntity extends TenantScopedEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() {
        return createdAt;
    }
}
