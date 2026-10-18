package com.retailerp.pricing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    @EntityGraph(attributePaths = "product")
    List<Promotion> findAllByOrderByActiveDescNameAsc();

    /** Automatic promotions (no coupon) that are switched on; dates are checked in Java. */
    @EntityGraph(attributePaths = "product")
    @Query("select p from Promotion p where p.active = true and p.couponCode is null")
    List<Promotion> automatic();

    @EntityGraph(attributePaths = "product")
    @Query("select p from Promotion p where lower(p.couponCode) = lower(:code)")
    Optional<Promotion> byCoupon(@Param("code") String code);
}
