package com.retailerp.payables;

import java.math.BigDecimal;
import java.util.List;
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

public interface SupplierBillRepository extends JpaRepository<SupplierBill, UUID> {

    @EntityGraph(attributePaths = {"supplier", "purchaseOrder"})
    @Query("""
            select b from SupplierBill b join b.supplier s
            where (:status is null or b.status = :status)
              and (:supplierId is null or s.id = :supplierId)
              and (:q = '' or lower(b.number) like lower(concat('%', :q, '%')) or lower(s.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(b.supplierReference, '')) like lower(concat('%', :q, '%')))
            """)
    Page<SupplierBill> search(@Param("status") BillStatus status, @Param("supplierId") UUID supplierId, @Param("q") String q,
            Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "purchaseOrder", "lines"})
    @Query("select b from SupplierBill b where b.id = :id")
    Optional<SupplierBill> findWithLines(@Param("id") UUID id);

    /** SELECT ... FOR UPDATE: two payments on the same bill are processed one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SupplierBill b where b.id = :id")
    Optional<SupplierBill> findForUpdate(@Param("id") UUID id);

    Optional<SupplierBill> findByPurchaseOrderId(UUID purchaseOrderId);

    /** Approved bills of a supplier (the "+" side of the statement). */
    @Query("select b from SupplierBill b where b.supplier.id = :supplierId and b.status in ('OPEN', 'PARTIALLY_PAID', 'PAID') order by b.billDate, b.createdAt")
    List<SupplierBill> approvedFor(@Param("supplierId") UUID supplierId);

    @Query("select coalesce(sum(b.total - b.amountPaid), 0) from SupplierBill b where b.status in ('OPEN', 'PARTIALLY_PAID')")
    BigDecimal outstandingTotal();
}
