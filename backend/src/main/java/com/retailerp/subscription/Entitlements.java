package com.retailerp.subscription;

import java.time.Instant;
import java.util.Set;

/**
 * What the current tenant may use right now: the effective plan (the trial plan while the trial runs), its modules
 * plus add-ons, and the limits. Computed per request from the subscription; never stored.
 */
public record Entitlements(
        String planCode,
        String planName,
        String basePlanCode,
        boolean trialActive,
        Instant trialEndsAt,
        Set<Module> modules,
        Set<Module> addons,
        Integer maxUsers,
        Integer maxWarehouses) {

    public boolean has(Module module) {
        return modules.contains(module);
    }
}
