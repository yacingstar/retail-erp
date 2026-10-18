package com.retailerp.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link JpaSpecificationExecutor} adds findAll(Specification, Pageable): queries built from optional filters
 * at runtime (see StockService#movements), instead of one repository method per filter combination.
 */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {

    @Query("select coalesce(sum(m.quantity), 0) from StockMovement m where m.product.id = :productId and m.warehouse.id = :warehouseId")
    long levelOf(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    /** Current stock per product and warehouse = SUM of all movements (the "derived" stock level). */
    @Query("""
            select new com.retailerp.inventory.StockDtos$StockLevel(
                p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, sum(m.quantity), p.reorderLevel)
            from StockMovement m join m.product p join m.warehouse w
            group by p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, p.reorderLevel
            order by p.name, w.code
            """)
    List<StockDtos.StockLevel> allLevels();

    @Query("""
            select new com.retailerp.inventory.StockDtos$StockLevel(
                p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, sum(m.quantity), p.reorderLevel)
            from StockMovement m join m.product p join m.warehouse w
            where w.id = :warehouseId
            group by p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, p.reorderLevel
            order by p.name
            """)
    List<StockDtos.StockLevel> levelsInWarehouse(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select new com.retailerp.inventory.StockDtos$StockLevel(
                p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, sum(m.quantity), p.reorderLevel)
            from StockMovement m join m.product p join m.warehouse w
            where p.id = :productId
            group by p.id, p.sku, p.name, p.unit, w.id, w.code, w.name, p.reorderLevel
            order by w.code
            """)
    List<StockDtos.StockLevel> levelsOfProduct(@Param("productId") UUID productId);

    /** Active products whose total stock (all warehouses) is at or below their reorder level. */
    @Query("""
            select new com.retailerp.inventory.StockDtos$LowStock(p.id, p.sku, p.name, p.unit,
                coalesce((select sum(m.quantity) from StockMovement m where m.product = p), 0), p.reorderLevel)
            from Product p
            where p.active = true
              and coalesce((select sum(m.quantity) from StockMovement m where m.product = p), 0) <= p.reorderLevel
            order by p.name
            """)
    List<StockDtos.LowStock> lowStock();

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<StockMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(String referenceType, UUID referenceId);
}
