package com.retailerp.pricing;

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

import com.retailerp.pricing.PricingDtos.PriceListRequest;
import com.retailerp.pricing.PricingDtos.PriceListResponse;
import com.retailerp.pricing.PricingDtos.PriceListSummary;
import com.retailerp.pricing.PricingDtos.PromotionRequest;
import com.retailerp.pricing.PricingDtos.PromotionResponse;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Everyone can read (cashiers see which promotions run); only managers change them. */
@RestController
@RequestMapping("/api")
@RequiresModule(Module.PROMOTIONS)
@PreAuthorize(Access.ANY)
public class PricingController {

    private final PricingAdminService admin;

    public PricingController(PricingAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/price-lists")
    public List<PriceListSummary> priceLists() {
        return admin.priceLists();
    }

    @GetMapping("/price-lists/{id}")
    public PriceListResponse priceList(@PathVariable UUID id) {
        return admin.priceList(id);
    }

    @PostMapping("/price-lists")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public PriceListResponse createPriceList(@Valid @RequestBody PriceListRequest request) {
        return admin.createPriceList(request);
    }

    @PutMapping("/price-lists/{id}")
    @PreAuthorize(Access.MANAGE)
    public PriceListResponse updatePriceList(@PathVariable UUID id, @Valid @RequestBody PriceListRequest request) {
        return admin.updatePriceList(id, request);
    }

    @DeleteMapping("/price-lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.MANAGE)
    public void deletePriceList(@PathVariable UUID id) {
        admin.deletePriceList(id);
    }

    @GetMapping("/promotions")
    public List<PromotionResponse> promotions() {
        return admin.promotions();
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public PromotionResponse createPromotion(@Valid @RequestBody PromotionRequest request) {
        return admin.createPromotion(request);
    }

    @PutMapping("/promotions/{id}")
    @PreAuthorize(Access.MANAGE)
    public PromotionResponse updatePromotion(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
        return admin.updatePromotion(id, request);
    }

    @DeleteMapping("/promotions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Access.MANAGE)
    public void deletePromotion(@PathVariable UUID id) {
        admin.deletePromotion(id);
    }
}
