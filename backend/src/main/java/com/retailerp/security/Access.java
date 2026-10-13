package com.retailerp.security;

/**
 * SpEL expressions for {@code @PreAuthorize}, kept in one place so the role rules are easy to review.
 * (Annotation values must be compile-time constants, hence plain strings.)
 */
public final class Access {

    /** User management. */
    public static final String ADMIN = "hasRole('ADMIN')";
    /** Catalog, purchasing, stock adjustments, cancellations. */
    public static final String MANAGE = "hasAnyRole('ADMIN', 'MANAGER')";
    /** Any logged-in user of the tenant (including cashiers). */
    public static final String ANY = "isAuthenticated()";

    private Access() {
    }
}
