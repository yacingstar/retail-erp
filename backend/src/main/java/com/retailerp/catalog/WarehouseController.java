package com.retailerp.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.catalog.CatalogDtos.WarehouseRequest;
import com.retailerp.catalog.CatalogDtos.WarehouseResponse;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

@RequiresModule(Module.INVENTORY)
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final CatalogService catalog;

    public WarehouseController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize(Access.ANY)
    public List<WarehouseResponse> list() {
        return catalog.listWarehouses();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.ANY)
    public WarehouseResponse get(@PathVariable UUID id) {
        return catalog.getWarehouse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return catalog.createWarehouse(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Access.MANAGE)
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return catalog.updateWarehouse(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.MANAGE)
    public void delete(@PathVariable UUID id) {
        catalog.deleteWarehouse(id);
    }
}
