package com.retailerp.purchasing;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    /** {@code @EntityGraph}: fetch the supplier in the same SQL (JOIN) instead of one extra query per row ("N+1"). */
    @EntityGraph(attributePaths = {"supplier", "warehouse"})
    @Query("""
            select po from PurchaseOrder po join po.supplier s
            where (:status is null or po.status = :status)
              and (:q = '' or lower(po.number) like lower(concat('%', :q, '%')) or lower(s.name) like lower(concat('%', :q, '%')))
            """)
    Page<PurchaseOrder> search(@Param("status") PurchaseOrderStatus status, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "warehouse", "lines", "lines.product"})
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findWithLines(@Param("id") UUID id);

    long countByStatusIn(Collection<PurchaseOrderStatus> statuses);
}
