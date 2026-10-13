package com.retailerp.common;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * A tenant-owned entity that can be edited: adds updated_at and an optimistic-locking version.
 * <p>
 * {@code @Version}: Hibernate adds {@code WHERE version = ?} to every UPDATE and increments it. If two users
 * edit the same row at the same time, the second save fails instead of silently overwriting the first.
 */
@MappedSuperclass
public abstract class AuditedEntity extends TenantOwnedEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
