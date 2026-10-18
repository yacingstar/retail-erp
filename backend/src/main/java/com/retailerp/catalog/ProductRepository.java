package com.retailerp.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data generates the implementation of this interface at startup: CRUD from JpaRepository, plus queries
 * from method names or {@code @Query}. Thanks to {@code @TenantId}, every query is limited to the current tenant.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("""
            select p from Product p
            where (:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) like lower(concat('%', :q, '%')))
              and (:category = '' or p.category = :category)
              and (:activeOnly = false or p.active = true)
            """)
    Page<Product> search(@Param("q") String q, @Param("category") String category, @Param("activeOnly") boolean activeOnly,
            Pageable pageable);

    @Query("select distinct p.category from Product p where p.category is not null and p.active = true order by p.category")
    List<String> categories();

    long countByActiveTrue();
}
