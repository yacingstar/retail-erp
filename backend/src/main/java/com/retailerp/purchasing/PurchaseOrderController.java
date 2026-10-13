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
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderSummary;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** State changes are POST sub-resources (/order, /receive, /cancel): they're actions, not field edits. */
@RequiresModule(Module.PURCHASING)
@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize(Access.MANAGE)
public class PurchaseOrderController {

    private final PurchasingService purchasing;

    public PurchaseOrderController(PurchasingService purchasing) {
        this.purchasing = purchasing;
    }

    @GetMapping
    public PageResponse<PurchaseOrderSummary> search(@RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchasing.searchOrders(status, q, pageable);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable UUID id) {
        return purchasing.getOrder(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchasing.createOrder(request);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchasing.updateOrder(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        purchasing.deleteOrder(id);
    }

    @PostMapping("/{id}/order")
    public PurchaseOrderResponse order(@PathVariable UUID id) {
        return purchasing.markOrdered(id);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrderResponse receive(@PathVariable UUID id) {
        return purchasing.receive(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancel(@PathVariable UUID id) {
        return purchasing.cancel(id);
    }
}
