package com.retailerp.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogService;
import com.retailerp.catalog.Product;
import com.retailerp.common.NotFoundException;
import com.retailerp.pricing.PricingDtos.PriceListItemResponse;
import com.retailerp.pricing.PricingDtos.PriceListRequest;
import com.retailerp.pricing.PricingDtos.PriceListResponse;
import com.retailerp.pricing.PricingDtos.PriceListSummary;
import com.retailerp.pricing.PricingDtos.PromotionRequest;
import com.retailerp.pricing.PricingDtos.PromotionResponse;

/** Managing price lists and promotions (the calculation itself lives in PricingService). */
@Service
@Transactional
public class PricingAdminService {

    private final PriceListRepository priceLists;
    private final PromotionRepository promotions;
    private final CatalogService catalog;

    public PricingAdminService(PriceListRepository priceLists, PromotionRepository promotions, CatalogService catalog) {
        this.priceLists = priceLists;
        this.promotions = promotions;
        this.catalog = catalog;
    }

    // --- price lists ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PriceListSummary> priceLists() {
        return priceLists.findAllByOrderByNameAsc().stream().map(p -> new PriceListSummary(p.getId(), p.getName(), p.isActive())).toList();
    }

    @Transactional(readOnly = true)
    public PriceListResponse priceList(UUID id) {
        return toResponse(priceListEntity(id));
    }

    public PriceListResponse createPriceList(PriceListRequest request) {
        PriceList list = new PriceList(request.name().trim());
        apply(list, request);
        return toResponse(priceLists.saveAndFlush(list));
    }

    public PriceListResponse updatePriceList(UUID id, PriceListRequest request) {
        PriceList list = priceListEntity(id);
        apply(list, request);
        priceLists.flush();
        return toResponse(list);
    }

    public void deletePriceList(UUID id) {
        priceLists.delete(priceListEntity(id));
        priceLists.flush(); // still assigned to customers → FK violation → 409
    }

    /** For other modules (customers): the list must exist in this tenant. */
    @Transactional(readOnly = true)
    public PriceList requirePriceList(UUID id) {
        return priceLists.findById(id).orElseThrow(() -> new NotFoundException("Price list", id));
    }

    private void apply(PriceList list, PriceListRequest request) {
        Set<UUID> seen = new HashSet<>();
        List<PriceListItem> items = (request.items() == null ? List.<PricingDtos.PriceListItemRequest>of() : request.items()).stream().map(i -> {
            if (!seen.add(i.productId())) {
                throw new IllegalArgumentException("A product can appear only once in a price list.");
            }
            return new PriceListItem(catalog.product(i.productId()), i.price());
        }).toList();
        list.update(request.name().trim(), request.active() == null || request.active(), items);
    }

    private PriceList priceListEntity(UUID id) {
        return priceLists.findWithItems(id).orElseThrow(() -> new NotFoundException("Price list", id));
    }

    private static PriceListResponse toResponse(PriceList p) {
        return new PriceListResponse(p.getId(), p.getName(), p.isActive(), p.getItems().stream()
                .map(i -> new PriceListItemResponse(i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                        i.getProduct().getSalePrice(), i.getPrice()))
                .sorted((a, b) -> a.productName().compareToIgnoreCase(b.productName())).toList());
    }

    // --- promotions ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PromotionResponse> promotions() {
        return promotions.findAllByOrderByActiveDescNameAsc().stream().map(PricingAdminService::toResponse).toList();
    }

    public PromotionResponse createPromotion(PromotionRequest request) {
        Promotion promotion = new Promotion(request.type());
        apply(promotion, request);
        return toResponse(promotions.saveAndFlush(promotion)); // duplicate coupon code → unique index → 409
    }

    public PromotionResponse updatePromotion(UUID id, PromotionRequest request) {
        Promotion promotion = promotion(id);
        apply(promotion, request);
        promotions.flush();
        return toResponse(promotion);
    }

    public void deletePromotion(UUID id) {
        promotions.delete(promotion(id));
    }

    private void apply(Promotion promotion, PromotionRequest r) {
        PromotionScope scope = r.scope() == null ? PromotionScope.ALL : r.scope();
        boolean percent = r.type() == PromotionType.PERCENT_OFF || r.type() == PromotionType.ORDER_PERCENT_OFF;
        if (percent && r.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("A percentage can't be more than 100.");
        }
        if (r.type() == PromotionType.BUY_X_GET_Y && (r.buyQty() == null || r.getQty() == null)) {
            throw new IllegalArgumentException("Buy X get Y needs both quantities (e.g. buy 2, get 1).");
        }
        if (r.type() == PromotionType.ORDER_PERCENT_OFF && scope != PromotionScope.ALL) {
            throw new IllegalArgumentException("An order discount applies to the whole basket (scope ALL).");
        }
        if (scope == PromotionScope.CATEGORY && (r.category() == null || r.category().isBlank())) {
            throw new IllegalArgumentException("Choose the category this promotion applies to.");
        }
        if (scope == PromotionScope.PRODUCT && r.productId() == null) {
            throw new IllegalArgumentException("Choose the product this promotion applies to.");
        }
        if (r.startsOn() != null && r.endsOn() != null && r.endsOn().isBefore(r.startsOn())) {
            throw new IllegalArgumentException("The end date can't be before the start date.");
        }
        Product product = scope == PromotionScope.PRODUCT ? catalog.product(r.productId()) : null;
        String coupon = r.couponCode() == null || r.couponCode().isBlank() ? null : r.couponCode().trim().toUpperCase();
        promotion.update(r.name().trim(), r.type(), r.value(), r.type() == PromotionType.BUY_X_GET_Y ? r.buyQty() : null,
                r.type() == PromotionType.BUY_X_GET_Y ? r.getQty() : null,
                r.type() == PromotionType.ORDER_PERCENT_OFF ? r.minOrderAmount() : null, scope,
                scope == PromotionScope.CATEGORY ? r.category().trim() : null, product, coupon, r.startsOn(), r.endsOn(),
                r.active() == null || r.active());
    }

    private Promotion promotion(UUID id) {
        return promotions.findById(id).orElseThrow(() -> new NotFoundException("Promotion", id));
    }

    private static PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(p.getId(), p.getName(), p.getType(), p.getValue(), p.getBuyQty(), p.getGetQty(), p.getMinOrderAmount(),
                p.getScope(), p.getCategory(), p.getProduct() == null ? null : p.getProduct().getId(),
                p.getProduct() == null ? null : p.getProduct().getName(), p.getCouponCode(), p.getStartsOn(), p.getEndsOn(), p.isActive(),
                p.runsOn(LocalDate.now()));
    }
}
