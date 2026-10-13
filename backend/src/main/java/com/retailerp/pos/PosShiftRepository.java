package com.retailerp.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.retailerp.invoicing.PaymentMethod;

public interface PosShiftRepository extends JpaRepository<PosShift, UUID> {

    @EntityGraph(attributePaths = "warehouse")
    Optional<PosShift> findFirstByOpenedByAndStatus(UUID openedBy, ShiftStatus status);

    @EntityGraph(attributePaths = "warehouse")
    @Query("select s from PosShift s")
    Page<PosShift> findPage(Pageable pageable);

    /** Number and value of confirmed sales made during the shift. */
    @Query("""
            select new com.retailerp.pos.PosDtos$SalesTotals(count(so), coalesce(sum(so.total), 0))
            from SalesOrder so where so.posShiftId = :shiftId and so.status = 'CONFIRMED'
            """)
    PosDtos.SalesTotals salesTotals(@Param("shiftId") UUID shiftId);

    /** Payments taken during the shift, per method (cash, card...). */
    @Query("""
            select new com.retailerp.pos.PosDtos$MethodTotal(p.method, sum(p.amount))
            from Payment p where p.invoice.salesOrder.posShiftId = :shiftId
            group by p.method order by p.method
            """)
    List<PosDtos.MethodTotal> paymentsByMethod(@Param("shiftId") UUID shiftId);

    default BigDecimal cashTaken(UUID shiftId) {
        return paymentsByMethod(shiftId).stream().filter(t -> t.method() == PaymentMethod.CASH).map(PosDtos.MethodTotal::amount)
                .findFirst().orElse(BigDecimal.ZERO);
    }
}
