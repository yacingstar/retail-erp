package com.retailerp.returns;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {

    @EntityGraph(attributePaths = {"salesOrder", "customer"})
    @Query("""
            select r from SalesReturn r left join r.customer c
            where :q = '' or lower(r.number) like lower(concat('%', :q, '%'))
               or lower(r.salesOrder.number) like lower(concat('%', :q, '%')) or lower(c.name) like lower(concat('%', :q, '%'))
            """)
    Page<SalesReturn> search(@Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"salesOrder", "invoice", "customer", "warehouse", "lines", "lines.product"})
    @Query("select r from SalesReturn r where r.id = :id")
    Optional<SalesReturn> findWithLines(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines"})
    List<SalesReturn> findBySalesOrderIdOrderByCreatedAtAsc(UUID salesOrderId);

    /** What was already returned from one sales order line: quantity and money, summed over all its returns. */
    @Query("""
            select new com.retailerp.returns.ReturnDtos$Returned(l.salesOrderLine.id, sum(l.quantity), sum(l.lineSubtotal),
                sum(l.lineDiscount), sum(l.lineTax))
            from SalesReturnLine l where l.salesOrderLine.salesOrder.id = :orderId group by l.salesOrderLine.id
            """)
    List<ReturnDtos.Returned> returnedPerLine(@Param("orderId") UUID orderId);
}
