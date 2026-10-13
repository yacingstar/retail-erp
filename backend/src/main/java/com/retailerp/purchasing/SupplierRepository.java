package com.retailerp.purchasing;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query("select s from Supplier s where :q = '' or lower(s.name) like lower(concat('%', :q, '%')) or lower(s.email) like lower(concat('%', :q, '%'))")
    Page<Supplier> search(@Param("q") String q, Pageable pageable);

    long countByActiveTrue();
}
