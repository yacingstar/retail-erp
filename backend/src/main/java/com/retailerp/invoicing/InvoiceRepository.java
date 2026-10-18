package com.retailerp.invoicing;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"customer", "salesOrder"})
    @Query("""
            select i from Invoice i left join i.customer c
            where (:status is null or i.status = :status)
              and (:q = '' or lower(i.number) like lower(concat('%', :q, '%')) or lower(c.name) like lower(concat('%', :q, '%')))
            """)
    Page<Invoice> search(@Param("status") InvoiceStatus status, @Param("q") String q, Pageable pageable);

    Optional<Invoice> findBySalesOrderId(UUID salesOrderId);

    /** SELECT ... FOR UPDATE: two payments on the same invoice are processed one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(i.total - i.amountPaid - i.amountCredited), 0) from Invoice i where i.status in ('ISSUED', 'PARTIALLY_PAID')")
    BigDecimal outstandingTotal();
}
