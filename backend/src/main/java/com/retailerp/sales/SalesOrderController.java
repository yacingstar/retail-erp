package com.retailerp.sales;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import com.retailerp.sales.SalesDtos.SalesOrderRequest;
import com.retailerp.sales.SalesDtos.SalesOrderResponse;
import com.retailerp.sales.SalesDtos.SalesOrderSummary;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

@RequiresModule(Module.SALES)
@RestController
@RequestMapping("/api/sales-orders")
@PreAuthorize(Access.ANY)
public class SalesOrderController {

    private final SalesService sales;

    public SalesOrderController(SalesService sales) {
        this.sales = sales;
    }

    @GetMapping
    public PageResponse<SalesOrderSummary> search(@RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return sales.searchOrders(status, q, pageable);
    }

    @GetMapping("/{id}")
    public SalesOrderResponse get(@PathVariable UUID id) {
        return sales.getOrder(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderResponse create(@Valid @RequestBody SalesOrderRequest request) {
        return sales.createOrder(request);
    }

    @PutMapping("/{id}")
    public SalesOrderResponse update(@PathVariable UUID id, @Valid @RequestBody SalesOrderRequest request) {
        return sales.updateOrder(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        sales.deleteOrder(id);
    }

    @PostMapping("/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable UUID id) {
        return sales.confirm(id);
    }

    /** Spring MVC injects the current {@link Authentication} as a controller parameter. */
    @PostMapping("/{id}/cancel")
    public SalesOrderResponse cancel(@PathVariable UUID id, Authentication authentication) {
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        return sales.cancel(id, manager);
    }
}
