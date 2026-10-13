package com.retailerp.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogDtos.ProductRequest;
import com.retailerp.catalog.CatalogDtos.ProductResponse;
import com.retailerp.catalog.CatalogDtos.WarehouseRequest;
import com.retailerp.catalog.CatalogDtos.WarehouseResponse;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;

@Service
@Transactional
public class CatalogService {

    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public CatalogService(ProductRepository products, WarehouseRepository warehouses) {
        this.products = products;
        this.warehouses = warehouses;
    }

    // --- products -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(String q, boolean activeOnly, Pageable pageable) {
        return PageResponse.of(products.search(q == null ? "" : q.trim(), activeOnly, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(product(id));
    }

    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product(request.sku().trim());
        apply(product, request);
        // saveAndFlush so a duplicate SKU fails here (unique constraint → 409) rather than at commit.
        return ProductResponse.from(products.saveAndFlush(product));
    }

    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = product(id);
        apply(product, request);
        // No save() needed: the entity is "managed", so Hibernate detects the changes and UPDATEs at commit
        // ("dirty checking"). We flush to surface constraint violations now.
        products.flush();
        return ProductResponse.from(product);
    }

    public void deleteProduct(UUID id) {
        products.delete(product(id));
        products.flush(); // referenced by movements/orders → FK violation → 409 (deactivate it instead)
    }

    public Product product(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private static void apply(Product product, ProductRequest r) {
        product.update(r.sku().trim(), r.name().trim(), r.description(), blankTo(r.unit(), "pcs"), r.salePrice(), r.costPrice(),
                r.taxRate() == null ? BigDecimal.ZERO : r.taxRate(), r.reorderLevel() == null ? 0 : r.reorderLevel(),
                r.active() == null || r.active());
    }

    // --- warehouses -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses() {
        return warehouses.findAllByOrderByCodeAsc().stream().map(WarehouseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouse(UUID id) {
        return WarehouseResponse.from(warehouse(id));
    }

    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        Warehouse warehouse = new Warehouse(request.code().trim());
        warehouse.update(request.code().trim(), request.name().trim(), request.address(), request.active() == null || request.active());
        return WarehouseResponse.from(warehouses.saveAndFlush(warehouse));
    }

    public WarehouseResponse updateWarehouse(UUID id, WarehouseRequest request) {
        Warehouse warehouse = warehouse(id);
        warehouse.update(request.code().trim(), request.name().trim(), request.address(), request.active() == null || request.active());
        warehouses.flush();
        return WarehouseResponse.from(warehouse);
    }

    public void deleteWarehouse(UUID id) {
        warehouses.delete(warehouse(id));
        warehouses.flush();
    }

    public Warehouse warehouse(UUID id) {
        return warehouses.findById(id).orElseThrow(() -> new NotFoundException("Warehouse", id));
    }

    /** For documents that move stock: the warehouse must exist and be active. */
    public Warehouse activeWarehouse(UUID id) {
        Warehouse warehouse = warehouse(id);
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("Warehouse " + warehouse.getCode() + " is inactive.");
        }
        return warehouse;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
