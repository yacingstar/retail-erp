package com.retailerp.subscription;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * The tenant's plan, trial and add-ons. The primary key IS the tenant id (one row per tenant); RLS limits every
 * query to the current tenant. The app may insert it (at registration) and read it, never update it: the
 * database role has no UPDATE privilege. Platform admins change it through a SECURITY DEFINER function.
 */
@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 20, updatable = false)
    private String planCode;

    @Column(name = "trial_plan_code", length = 20, updatable = false)
    private String trialPlanCode;

    @Column(name = "trial_ends_at", updatable = false)
    private Instant trialEndsAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_addons", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "module", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Module> addons = EnumSet.noneOf(Module.class);

    protected TenantSubscription() {
    }

    TenantSubscription(UUID tenantId, String planCode, String trialPlanCode, Instant trialEndsAt) {
        this.tenantId = tenantId;
        this.planCode = planCode;
        this.trialPlanCode = trialPlanCode;
        this.trialEndsAt = trialEndsAt;
        this.updatedAt = Instant.now();
    }

    public boolean trialActive(Instant now) {
        return trialPlanCode != null && trialEndsAt != null && trialEndsAt.isAfter(now);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getTrialPlanCode() {
        return trialPlanCode;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public Set<Module> getAddons() {
        return addons;
    }
}
