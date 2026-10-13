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
}
