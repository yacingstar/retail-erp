package com.retailerp.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogService;
import com.retailerp.catalog.Product;
import com.retailerp.catalog.Warehouse;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.PageResponse;
import com.retailerp.inventory.StockDtos.AdjustmentRequest;
import com.retailerp.inventory.StockDtos.LowStock;
import com.retailerp.inventory.StockDtos.MovementResponse;
import com.retailerp.inventory.StockDtos.StockLevel;
import com.retailerp.inventory.StockDtos.TransferRequest;
import com.retailerp.security.CurrentUser;

/**
 * The ONLY way stock changes. Other modules (purchasing, sales) call {@link #addStock} / {@link #removeStock}.
 * <p>
 * Concurrency: two cashiers selling the last item at the same time must not both succeed. Before reading a level
 * and writing a movement we take a Postgres advisory lock for that (product, warehouse) pair; it's held until the
 * transaction ends, so the "check then insert" runs one at a time per pair. Locks are taken in a fixed (sorted)
 * order to avoid deadlocks when one document touches several products.
 */
@Service
@Transactional
public class StockService {

    private final StockMovementRepository movements;
    private final CatalogService catalog;
    private final JdbcTemplate jdbc;

    public StockService(StockMovementRepository movements, CatalogService catalog, JdbcTemplate jdbc) {
        this.movements = movements;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    /** A product/warehouse pair whose stock is about to change. */
    public record StockKey(UUID productId, UUID warehouseId) implements Comparable<StockKey> {

        @Override
        public int compareTo(StockKey other) {
            int byProduct = productId.compareTo(other.productId);
            return byProduct != 0 ? byProduct : warehouseId.compareTo(other.warehouseId);
        }
    }

    // --- used by other modules (must run inside the caller's transaction) -------------------------------------------

    /** Locks the given pairs until the current transaction ends. Call before checking levels. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Collection<StockKey> keys) {
        keys.stream().distinct().sorted().forEach(key -> jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> null,
                "stock:" + key.productId() + ":" + key.warehouseId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int level(UUID productId, UUID warehouseId) {
        return Math.toIntExact(movements.levelOf(productId, warehouseId));
    }

    /** Fails with a clear message if any (product, warehouse) has less than the requested quantity. Lock first! */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(Map<Product, Integer> quantities, Warehouse warehouse) {
        quantities.forEach((product, requested) -> {
            int available = level(product.getId(), warehouse.getId());
            if (available < requested) {
                throw new BusinessRuleException("Not enough stock for " + product.getSku() + " in " + warehouse.getCode()
                        + ": available " + available + ", requested " + requested + ".");
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement addStock(Product product, Warehouse warehouse, int quantity, MovementType type, String referenceType,
            UUID referenceId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return movements.save(new StockMovement(product, warehouse, quantity, type, referenceType, referenceId, note, CurrentUser.id()));
    }

    /** Records a stock decrease. The caller must have locked and checked availability (see requireAvailable). */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement removeStock(Product product, Warehouse warehouse, int quantity, MovementType type, String referenceType,
            UUID referenceId, String note) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return movements.save(new StockMovement(product, warehouse, -quantity, type, referenceType, referenceId, note, CurrentUser.id()));
    }

    // --- API operations ----------------------------------------------------------------------------------------------

    public MovementResponse adjust(AdjustmentRequest request) {
        if (request.quantity() == 0) {
            throw new IllegalArgumentException("quantity must not be 0");
        }
        Product product = catalog.product(request.productId());
        Warehouse warehouse = catalog.activeWarehouse(request.warehouseId());
        if (request.quantity() > 0) {
            return MovementResponse.from(addStock(product, warehouse, request.quantity(), MovementType.ADJUSTMENT, null, null, request.note()));
        }
        int removed = -request.quantity();
        lock(List.of(new StockKey(product.getId(), warehouse.getId())));
        requireAvailable(Map.of(product, removed), warehouse);
        return MovementResponse.from(removeStock(product, warehouse, removed, MovementType.ADJUSTMENT, null, null, request.note()));
    }

    /** A transfer is two movements in one transaction: out of one warehouse, into another. */
    public List<MovementResponse> transfer(TransferRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new IllegalArgumentException("Source and destination warehouse must be different.");
        }
        Product product = catalog.product(request.productId());
        Warehouse from = catalog.activeWarehouse(request.fromWarehouseId());
        Warehouse to = catalog.activeWarehouse(request.toWarehouseId());
        lock(List.of(new StockKey(product.getId(), from.getId()), new StockKey(product.getId(), to.getId())));
        requireAvailable(Map.of(product, request.quantity()), from);
        UUID transferId = UUID.randomUUID();
        StockMovement out = removeStock(product, from, request.quantity(), MovementType.TRANSFER_OUT, "TRANSFER", transferId, request.note());
        StockMovement in = addStock(product, to, request.quantity(), MovementType.TRANSFER_IN, "TRANSFER", transferId, request.note());
        return List.of(MovementResponse.from(out), MovementResponse.from(in));
    }

    @Transactional(readOnly = true)
    public List<StockLevel> levels(UUID warehouseId, String q) {
        List<StockLevel> levels = warehouseId == null ? movements.allLevels() : movements.levelsInWarehouse(warehouseId);
        if (q == null || q.isBlank()) {
            return levels;
        }
        String needle = q.trim().toLowerCase();
        return levels.stream()
                .filter(l -> l.productName().toLowerCase().contains(needle) || l.sku().toLowerCase().contains(needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLevel> levelsOfProduct(UUID productId) {
        catalog.product(productId); // 404 if it doesn't exist (or belongs to another tenant)
        return movements.levelsOfProduct(productId);
    }

    @Transactional(readOnly = true)
    public List<LowStock> lowStock() {
        return movements.lowStock();
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(UUID productId, UUID warehouseId, MovementType type, Pageable pageable) {
        Specification<StockMovement> spec = (root, query, cb) -> cb.conjunction();
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouse").get("id"), warehouseId));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        return PageResponse.of(movements.findAll(spec, pageable), MovementResponse::from);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movementsFor(String referenceType, UUID referenceId) {
        return movements.findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(referenceType, referenceId).stream()
                .map(MovementResponse::from).toList();
    }
}
