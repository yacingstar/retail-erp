package com.retailerp.sales;

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
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Cashiers can look up and add customers at the counter; only managers can delete them. */
@RequiresModule(Module.SALES)
@RestController
@RequestMapping("/api/customers")
@PreAuthorize(Access.ANY)
public class CustomerController {

    private final SalesService sales;
    private final StoreCreditService storeCredit;

    public CustomerController(SalesService sales, StoreCreditService storeCredit) {
        this.sales = sales;
        this.storeCredit = storeCredit;
    }

    @GetMapping
    public PageResponse<PartyResponse> search(@RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return sales.searchCustomers(q, pageable);
    }

    @GetMapping("/{id}")
    public PartyResponse get(@PathVariable UUID id) {
        return sales.getCustomer(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse create(@Valid @RequestBody PartyRequest request) {
        return sales.createCustomer(request);
    }

    @PutMapping("/{id}")
    public PartyResponse update(@PathVariable UUID id, @Valid @RequestBody PartyRequest request) {
        return sales.updateCustomer(id, request);
    }

    /** The customer's store credit balance and history. */
    @GetMapping("/{id}/store-credit")
    public StoreCreditService.CreditResponse storeCredit(@PathVariable UUID id) {
        return storeCredit.statement(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.MANAGE)
    public void delete(@PathVariable UUID id) {
        sales.deleteCustomer(id);
    }
}
