package com.retailerp.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads the logged-in user from the SecurityContext (filled in by the JWT filter for this request). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The user id (the JWT "sub" claim), or null when nobody is logged in. */
    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        return null;
    }

    /** The "name" claim of the token (the user's full name), or null. */
    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString("name") : null;
    }

    /** True for ADMIN and MANAGER (e.g. unlimited discounts, cancelling confirmed sales). */
    public static boolean isManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }
}
