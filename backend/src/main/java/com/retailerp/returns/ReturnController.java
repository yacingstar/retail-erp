package com.retailerp.returns;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.common.PageResponse;
import com.retailerp.returns.ReturnDtos.ReturnRequest;
import com.retailerp.returns.ReturnDtos.ReturnResponse;
import com.retailerp.returns.ReturnDtos.ReturnSummary;
import com.retailerp.returns.ReturnDtos.Returnable;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Cashiers can process returns (every return is recorded with who made it). */
@RestController
@RequestMapping("/api/returns")
@RequiresModule(Module.RETURNS)
@PreAuthorize(Access.ANY)
public class ReturnController {

    private final ReturnService returns;

    public ReturnController(ReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    public PageResponse<ReturnSummary> search(@RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return returns.search(q, pageable);
    }

    @GetMapping("/{id}")
    public ReturnResponse get(@PathVariable UUID id) {
        return returns.get(id);
    }

    /** What can still be returned from a sale (for the return form). */
    @GetMapping("/returnable/{salesOrderId}")
    public Returnable returnable(@PathVariable UUID salesOrderId) {
        return returns.returnable(salesOrderId);
    }

    @GetMapping("/by-sales-order/{salesOrderId}")
    public List<ReturnSummary> forSalesOrder(@PathVariable UUID salesOrderId) {
        return returns.forSalesOrder(salesOrderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnResponse create(@Valid @RequestBody ReturnRequest request) {
        return returns.create(request);
    }
}
