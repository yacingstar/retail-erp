package com.retailerp.payables;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.retailerp.payables.PayablesDtos.BillRequest;
import com.retailerp.payables.PayablesDtos.BillResponse;
import com.retailerp.payables.PayablesDtos.BillSummary;
import com.retailerp.payables.PayablesDtos.SupplierPaymentRequest;
import com.retailerp.payables.PayablesDtos.SupplierStatement;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Money going out: managers and admins only. */
@RestController
@RequestMapping("/api")
@RequiresModule(Module.SUPPLIER_BILLS)
@PreAuthorize(Access.MANAGE)
public class BillController {

    private final PayablesService payables;

    public BillController(PayablesService payables) {
        this.payables = payables;
    }

    @GetMapping("/bills")
    public PageResponse<BillSummary> search(@RequestParam(required = false) BillStatus status, @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "billDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return payables.search(status, supplierId, q, pageable);
    }

    @GetMapping("/bills/{id}")
    public BillResponse get(@PathVariable UUID id) {
        return payables.get(id);
    }

    @GetMapping("/bills/by-purchase-order/{purchaseOrderId}")
    public BillResponse forPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return payables.forPurchaseOrder(purchaseOrderId);
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse create(@Valid @RequestBody BillRequest request) {
        return payables.create(request);
    }

    @PutMapping("/bills/{id}")
    public BillResponse update(@PathVariable UUID id, @Valid @RequestBody BillRequest request) {
        return payables.update(id, request);
    }

    @PostMapping("/bills/{id}/approve")
    public BillResponse approve(@PathVariable UUID id) {
        return payables.approve(id);
    }

    @PostMapping("/bills/{id}/void")
    public BillResponse voidBill(@PathVariable UUID id) {
        return payables.voidBill(id);
    }

    @PostMapping("/bills/{id}/payments")
    public BillResponse pay(@PathVariable UUID id, @Valid @RequestBody SupplierPaymentRequest request) {
        return payables.pay(id, request);
    }

    @GetMapping("/suppliers/{supplierId}/statement")
    public SupplierStatement statement(@PathVariable UUID supplierId) {
        return payables.statement(supplierId);
    }
}
