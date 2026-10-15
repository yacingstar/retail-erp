package com.retailerp.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.retailerp.tenancy.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs right after Spring Security has verified the JWT: copies the token's tenant_id claim into
 * {@link TenantContext}, and always clears it when the request ends (threads are pooled and reused).
 * <p>
 * The tenant comes ONLY from the signed token, never from a header or URL the client could change.
 * Not a Spring bean on purpose: Boot would otherwise also register it as a plain servlet filter (running twice).
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String tenantId = jwt.getToken().getClaimAsString(JwtService.CLAIM_TENANT);
            if (tenantId != null) {
                TenantContext.set(UUID.fromString(tenantId));
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
