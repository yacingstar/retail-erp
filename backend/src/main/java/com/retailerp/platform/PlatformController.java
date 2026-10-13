package com.retailerp.platform;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.platform.PlatformService.LoginRequest;
import com.retailerp.platform.PlatformService.LoginResponse;
import com.retailerp.platform.PlatformService.SubscriptionUpdate;
import com.retailerp.platform.PlatformService.TenantRow;
import com.retailerp.security.Access;
import com.retailerp.subscription.SubscriptionController.PlanResponse;
import com.retailerp.subscription.SubscriptionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final PlatformService platform;
    private final SubscriptionService subscriptions;

    public PlatformController(PlatformService platform, SubscriptionService subscriptions) {
        this.platform = platform;
        this.subscriptions = subscriptions;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return platform.login(request);
    }

    @GetMapping("/tenants")
    @PreAuthorize(Access.PLATFORM)
    public List<TenantRow> tenants() {
        return platform.tenants();
    }

    @PutMapping("/tenants/{id}/subscription")
    @PreAuthorize(Access.PLATFORM)
    public TenantRow updateSubscription(@PathVariable UUID id, @Valid @RequestBody SubscriptionUpdate update) {
        return platform.updateSubscription(id, update);
    }

    @GetMapping("/plans")
    @PreAuthorize(Access.PLATFORM)
    public List<PlanResponse> plans() {
        return subscriptions.catalogue().stream().map(PlanResponse::from).toList();
    }
}
