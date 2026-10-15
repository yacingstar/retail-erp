package com.retailerp.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    List<PriceList> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("select pl from PriceList pl where pl.id = :id")
    Optional<PriceList> findWithItems(@Param("id") UUID id);

    @Query("""
            select i.price from PriceListItem i
            where i.priceList.id = :priceListId and i.priceList.active = true and i.product.id = :productId
            """)
    Optional<BigDecimal> priceOf(@Param("priceListId") UUID priceListId, @Param("productId") UUID productId);
}
