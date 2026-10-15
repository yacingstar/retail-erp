package com.retailerp.payables;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {

    List<SupplierPayment> findByBillIdOrderByPaidAtAsc(UUID billId);

    @Query("select p from SupplierPayment p join fetch p.bill b where b.supplier.id = :supplierId order by p.paidAt")
    List<SupplierPayment> forSupplier(@Param("supplierId") UUID supplierId);
}
