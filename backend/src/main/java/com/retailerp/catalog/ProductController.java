package com.retailerp.catalog;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.catalog.CatalogDtos.ProductRequest;
import com.retailerp.catalog.CatalogDtos.ProductResponse;
import com.retailerp.common.PageResponse;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

@RequiresModule(Module.INVENTORY)
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** e.g. GET /api/products?q=shirt&page=0&size=20&sort=name,asc. Spring builds the Pageable from the query string. */
    @GetMapping
    @PreAuthorize(Access.ANY)
    public PageResponse<ProductResponse> search(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return catalog.searchProducts(q, activeOnly, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.ANY)
    public ProductResponse get(@PathVariable UUID id) {
        return catalog.getProduct(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return catalog.createProduct(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Access.MANAGE)
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return catalog.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.MANAGE)
    public void delete(@PathVariable UUID id) {
        catalog.deleteProduct(id);
    }
}
