package com.retailerp.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreCreditRepository extends JpaRepository<StoreCreditEntry, UUID> {

    @Query("select coalesce(sum(e.amount), 0) from StoreCreditEntry e where e.customer.id = :customerId")
    BigDecimal balanceOf(@Param("customerId") UUID customerId);

    List<StoreCreditEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
