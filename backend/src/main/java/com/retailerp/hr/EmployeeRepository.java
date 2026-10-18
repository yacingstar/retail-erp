package com.retailerp.hr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    @Query("""
            select e from Employee e
            where (:active is null or e.active = :active)
              and (:q = '' or lower(e.fullName) like lower(concat('%', :q, '%')) or lower(coalesce(e.jobTitle, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Employee> search(@Param("active") Boolean active, @Param("q") String q, Pageable pageable);

    List<Employee> findByActiveTrueOrderByFullName();

    boolean existsByNssAndIdNot(String nss, UUID id);

    boolean existsByNss(String nss);
}
