package com.retailerp.hr;

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

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    @Query("select r from PayrollRun r")
    Page<PayrollRun> page(Pageable pageable);

    @EntityGraph(attributePaths = "payslips")
    @Query("select r from PayrollRun r where r.id = :id")
    Optional<PayrollRun> findWithPayslips(@Param("id") UUID id);

    /** SELECT ... FOR UPDATE: approving and paying the same run twice at once is processed one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PayrollRun r where r.id = :id")
    Optional<PayrollRun> findForUpdate(@Param("id") UUID id);

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    @Query("select p from Payslip p join fetch p.run r where p.employeeId = :employeeId order by r.periodYear desc, r.periodMonth desc")
    List<Payslip> payslipsOf(@Param("employeeId") UUID employeeId);
}
