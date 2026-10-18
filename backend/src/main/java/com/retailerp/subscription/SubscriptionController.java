package com.retailerp.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.catalog.WarehouseRepository;
import com.retailerp.security.Access;
import com.retailerp.user.UserRepository;

@RestController
@RequestMapping("/api/subscription")
@PreAuthorize(Access.ANY)
public class SubscriptionController {

    private final SubscriptionService subscriptions;
    private final UserRepository users;
    private final WarehouseRepository warehouses;

    public SubscriptionController(SubscriptionService subscriptions, UserRepository users, WarehouseRepository warehouses) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.warehouses = warehouses;
    }

    public record PlanResponse(String code, String name, Integer maxUsers, Integer maxWarehouses, Set<Module> modules) {

        public static PlanResponse from(Plan p) {
            return new PlanResponse(p.getCode(), p.getName(), p.getMaxUsers(), p.getMaxWarehouses(), p.getModules());
        }
    }

    public record ModuleResponse(Module code, String name) {
    }

    public record SubscriptionResponse(String planCode, String planName, String basePlanCode, boolean trialActive, Instant trialEndsAt,
            Set<Module> modules, Set<Module> addons, Integer maxUsers, Integer maxWarehouses, long activeUsers, long activeWarehouses,
            List<PlanResponse> plans, List<ModuleResponse> allModules) {
    }

    /** Everything the frontend needs to show/hide modules, the trial banner and the plan page. */
    @GetMapping
    @Transactional(readOnly = true)
    public SubscriptionResponse get() {
        Entitlements e = subscriptions.current();
        return new SubscriptionResponse(e.planCode(), e.planName(), e.basePlanCode(), e.trialActive(), e.trialEndsAt(), e.modules(),
                e.addons(), e.maxUsers(), e.maxWarehouses(), users.countByEnabledTrue(), warehouses.countByActiveTrue(),
                subscriptions.catalogue().stream().map(PlanResponse::from).toList(),
                java.util.Arrays.stream(Module.values()).map(m -> new ModuleResponse(m, m.displayName())).toList());
    }
}
