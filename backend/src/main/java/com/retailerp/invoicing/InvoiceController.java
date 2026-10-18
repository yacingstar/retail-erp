package com.retailerp.invoicing;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.common.PageResponse;
import com.retailerp.invoicing.InvoicingDtos.InvoiceResponse;
import com.retailerp.invoicing.InvoicingDtos.InvoiceSummary;
import com.retailerp.invoicing.InvoicingDtos.PaymentRequest;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

@RequiresModule(Module.INVOICING)
@RestController
@RequestMapping("/api/invoices")
@PreAuthorize(Access.ANY)
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping
    public PageResponse<InvoiceSummary> search(@RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return invoices.search(status, q, pageable);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoices.get(id);
    }

    @PostMapping("/{id}/payments")
    public InvoiceResponse pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return invoices.recordPayment(id, request);
    }
}
