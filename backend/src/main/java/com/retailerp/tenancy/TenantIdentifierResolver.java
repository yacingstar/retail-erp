package com.retailerp.tenancy;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant the current session belongs to.
 * <p>
 * Hibernate calls this when a session opens. Every entity field annotated with {@code @TenantId} is then
 * filled in automatically on insert and added to the WHERE clause of every query.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Used when there is no tenant: matches no rows, and inserts fail the tenant foreign key (fail closed). */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.get();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
