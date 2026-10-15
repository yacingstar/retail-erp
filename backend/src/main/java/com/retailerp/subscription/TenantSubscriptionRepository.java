package com.retailerp.subscription;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
}
