package com.retailerp.purchasing;

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

import com.retailerp.common.PageResponse;
import com.retailerp.common.PartyDtos.PartyRequest;
import com.retailerp.common.PartyDtos.PartyResponse;
import com.retailerp.security.Access;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize(Access.MANAGE)
public class SupplierController {

    private final PurchasingService purchasing;

    public SupplierController(PurchasingService purchasing) {
        this.purchasing = purchasing;
    }

    @GetMapping
    public PageResponse<PartyResponse> search(@RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return purchasing.searchSuppliers(q, pageable);
    }

    @GetMapping("/{id}")
    public PartyResponse get(@PathVariable UUID id) {
        return purchasing.getSupplier(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse create(@Valid @RequestBody PartyRequest request) {
        return purchasing.createSupplier(request);
    }

    @PutMapping("/{id}")
    public PartyResponse update(@PathVariable UUID id, @Valid @RequestBody PartyRequest request) {
        return purchasing.updateSupplier(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        purchasing.deleteSupplier(id);
    }
}
