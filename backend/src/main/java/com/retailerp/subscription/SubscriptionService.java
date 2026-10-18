package com.retailerp.subscription;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.NotFoundException;
import com.retailerp.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    public static final String DEFAULT_PLAN = "STARTER";
    public static final String TRIAL_PLAN = "PRO";

    private final TenantSubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final Duration trialLength;

    public SubscriptionService(TenantSubscriptionRepository subscriptions, PlanRepository plans,
            @Value("${app.subscription.trial-days:14}") int trialDays) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.trialLength = Duration.ofDays(trialDays);
    }

    /** Called at registration, inside the registration transaction, as the new tenant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void startTrial(UUID tenantId) {
        subscriptions.save(new TenantSubscription(tenantId, DEFAULT_PLAN, TRIAL_PLAN, Instant.now().plus(trialLength)));
    }

    public Entitlements current() {
        UUID tenantId = TenantContext.get();
        TenantSubscription subscription = subscriptions.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Subscription", tenantId));
        Map<String, Plan> byCode = plans.findAll().stream().collect(Collectors.toMap(Plan::getCode, Function.identity()));
        boolean trial = subscription.trialActive(Instant.now());
        Plan effective = byCode.get(trial ? subscription.getTrialPlanCode() : subscription.getPlanCode());
        Set<Module> modules = EnumSet.noneOf(Module.class);
        modules.addAll(effective.getModules());
        modules.addAll(subscription.getAddons());
        return new Entitlements(effective.getCode(), effective.getName(), subscription.getPlanCode(), trial,
                trial ? subscription.getTrialEndsAt() : null, modules, Set.copyOf(subscription.getAddons()), effective.getMaxUsers(),
                effective.getMaxWarehouses());
    }

    public void requireModule(Module module) {
        if (!current().has(module)) {
            throw new ModuleNotInPlanException(module);
        }
    }

    /** Throws if adding one more (active) user would exceed the plan. */
    public void checkUserLimit(long activeUsers) {
        Integer max = current().maxUsers();
        if (max != null && activeUsers >= max) {
            throw new PlanLimitException("users", max);
        }
    }

    public void checkWarehouseLimit(long activeWarehouses) {
        Integer max = current().maxWarehouses();
        if (max != null && activeWarehouses >= max) {
            throw new PlanLimitException("warehouses", max);
        }
    }

    public List<Plan> catalogue() {
        return plans.findAllByOrderBySortOrderAsc();
    }
}
