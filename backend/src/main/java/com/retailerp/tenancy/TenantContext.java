package com.retailerp.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the tenant of the current request in a ThreadLocal.
 * <p>
 * Servlet requests are handled by one thread from start to finish, so a ThreadLocal is a simple way to make
 * "who is the current tenant?" available everywhere (Hibernate, the DataSource) without passing it around.
 * It MUST be cleared at the end of every request, because threads are reused from a pool.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code action} as the given tenant, then restores the previous value. */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
