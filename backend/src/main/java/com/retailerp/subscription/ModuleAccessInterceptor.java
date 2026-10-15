package com.retailerp.subscription;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.retailerp.tenancy.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A Spring MVC interceptor runs around controller methods (after the security filters, so the tenant is known).
 * Here: if the target method or its class has {@link RequiresModule}, check the tenant's entitlements first.
 */
public class ModuleAccessInterceptor implements HandlerInterceptor {

    private final SubscriptionService subscriptions;

    public ModuleAccessInterceptor(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // No tenant (e.g. a platform admin token): nothing to check here; @PreAuthorize rejects non-tenant users.
        if (handler instanceof HandlerMethod method && TenantContext.get() != null) {
            RequiresModule required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresModule.class);
            if (required == null) {
                required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresModule.class);
            }
            if (required != null) {
                subscriptions.requireModule(required.value());
            }
        }
        return true;
    }
}
