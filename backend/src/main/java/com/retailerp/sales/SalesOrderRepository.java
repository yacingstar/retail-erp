package com.retailerp.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    @EntityGraph(attributePaths = {"customer", "warehouse"})
    @Query("""
            select so from SalesOrder so left join so.customer c
            where (:status is null or so.status = :status)
              and (:q = '' or lower(so.number) like lower(concat('%', :q, '%')) or lower(c.name) like lower(concat('%', :q, '%')))
            """)
    Page<SalesOrder> search(@Param("status") SalesOrderStatus status, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "warehouse", "lines", "lines.product"})
    @Query("select so from SalesOrder so where so.id = :id")
    Optional<SalesOrder> findWithLines(@Param("id") UUID id);

    @Query("select coalesce(sum(so.total), 0) from SalesOrder so where so.status = 'CONFIRMED' and so.confirmedAt >= :since")
    BigDecimal confirmedTotalSince(@Param("since") Instant since);

    @Query("""
            select new com.retailerp.sales.SalesDtos$DailySales(so.orderDate, sum(so.total), count(so))
            from SalesOrder so where so.status = 'CONFIRMED' and so.orderDate >= :from
            group by so.orderDate order by so.orderDate
            """)
    List<SalesDtos.DailySales> dailySales(@Param("from") LocalDate from);
}
