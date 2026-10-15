package com.retailerp.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.Product;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.Money;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.ModuleNotInPlanException;
import com.retailerp.subscription.SubscriptionService;

/**
 * Works out the price of a basket: base price (customer's price list, else the product's price), then promotions.
 * <p>
 * "Best for the customer, never stacked":
 * <ol>
 * <li>for each item, the single biggest item promotion (% off, amount off, buy X get Y);</li>
 * <li>the single biggest whole-order promotion (% off above a minimum amount);</li>
 * <li>whichever saves more in total wins; the other is not applied.</li>
 * </ol>
 * Pure calculation: it never saves anything, so the same code serves the live preview and the real sale.
 */
@Service
@Transactional(readOnly = true)
public class PricingService {

    private final PriceListRepository priceLists;
    private final PromotionRepository promotions;
    private final SubscriptionService subscriptions;

    public PricingService(PriceListRepository priceLists, PromotionRepository promotions, SubscriptionService subscriptions) {
        this.priceLists = priceLists;
        this.promotions = promotions;
        this.subscriptions = subscriptions;
    }

    /** One basket line as entered: unitPrice null = use the base price. */
    public record LineInput(Product product, int quantity, BigDecimal unitPrice) {
    }

    /** basePrice = what this customer normally pays; promoDiscount = money off from the winning promotion. */
    public record PricedLine(Product product, int quantity, BigDecimal basePrice, BigDecimal unitPrice, BigDecimal promoDiscount,
            String promotionName) {
    }

    public record PricingResult(List<PricedLine> lines, String couponCode) {
    }

    public PricingResult price(UUID priceListId, List<LineInput> inputs, String couponCode, LocalDate date) {
        boolean enabled = subscriptions.current().has(Module.PROMOTIONS);
        String code = couponCode == null || couponCode.isBlank() ? null : couponCode.trim();
        if (!enabled) {
            if (code != null) {
                throw new ModuleNotInPlanException(Module.PROMOTIONS);
            }
            return new PricingResult(inputs.stream().map(in -> {
                BigDecimal price = in.unitPrice() != null ? Money.round(in.unitPrice()) : in.product().getSalePrice();
                return new PricedLine(in.product(), in.quantity(), in.product().getSalePrice(), price, BigDecimal.ZERO, null);
            }).toList(), null);
        }

        // Candidate promotions: the automatic ones running today, plus the coupon's promotion if a valid code was given.
        List<Promotion> candidates = new ArrayList<>(promotions.automatic().stream().filter(p -> p.runsOn(date)).toList());
        if (code != null) {
            Promotion coupon = promotions.byCoupon(code).filter(p -> p.runsOn(date))
                    .orElseThrow(() -> new BusinessRuleException("Coupon \"" + code + "\" is not valid (unknown, inactive or expired)."));
            candidates.add(coupon);
        }

        // 1. Base prices and the best item promotion per line.
        List<PricedLine> itemPriced = new ArrayList<>();
        BigDecimal itemSavings = BigDecimal.ZERO;
        BigDecimal basket = BigDecimal.ZERO;
        for (LineInput in : inputs) {
            BigDecimal base = basePrice(in.product(), priceListId);
            BigDecimal unitPrice = in.unitPrice() != null ? Money.round(in.unitPrice()) : base;
            BigDecimal subtotal = Money.times(unitPrice, in.quantity());
            basket = basket.add(subtotal);
            Promotion best = null;
            BigDecimal bestAmount = BigDecimal.ZERO;
            for (Promotion p : candidates) {
                if (p.getType() == PromotionType.ORDER_PERCENT_OFF || !p.appliesTo(in.product())) {
                    continue;
                }
                BigDecimal amount = itemDiscount(p, unitPrice, in.quantity(), subtotal);
                if (amount.compareTo(bestAmount) > 0) {
                    best = p;
                    bestAmount = amount;
                }
            }
            itemSavings = itemSavings.add(bestAmount);
            itemPriced.add(new PricedLine(in.product(), in.quantity(), base, unitPrice, bestAmount, best == null ? null : best.getName()));
        }

        // 2. The best whole-order promotion.
        Promotion bestOrder = null;
        BigDecimal orderSavings = BigDecimal.ZERO;
        for (Promotion p : candidates) {
            if (p.getType() != PromotionType.ORDER_PERCENT_OFF) {
                continue;
            }
            BigDecimal minimum = p.getMinOrderAmount() == null ? BigDecimal.ZERO : p.getMinOrderAmount();
            if (basket.compareTo(minimum) >= 0) {
                BigDecimal amount = Money.percentOf(basket, p.getValue());
                if (amount.compareTo(orderSavings) > 0) {
                    bestOrder = p;
                    orderSavings = amount;
                }
            }
        }

        // 3. Whichever saves more wins (ties go to the item promotions).
        if (bestOrder == null || orderSavings.compareTo(itemSavings) <= 0) {
            return new PricingResult(itemPriced, code);
        }
        return new PricingResult(spread(itemPriced, orderSavings, basket, bestOrder.getName()), code);
    }

    /** The customer's normal price for a product: their price list's price if it has one, else the product's price. */
    public BigDecimal basePrice(Product product, UUID priceListId) {
        if (priceListId != null && subscriptions.current().has(Module.PROMOTIONS)) {
            return priceLists.priceOf(priceListId, product.getId()).orElse(product.getSalePrice());
        }
        return product.getSalePrice();
    }

    private static BigDecimal itemDiscount(Promotion p, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
        return switch (p.getType()) {
            case PERCENT_OFF -> Money.percentOf(subtotal, p.getValue());
            case AMOUNT_OFF -> Money.times(p.getValue().min(unitPrice), quantity); // never below zero per unit
            case BUY_X_GET_Y -> {
                int group = p.getBuyQty() + p.getGetQty();
                int free = (quantity / group) * p.getGetQty();
                yield Money.times(unitPrice, free);
            }
            case ORDER_PERCENT_OFF -> BigDecimal.ZERO;
        };
    }

    /**
     * An order discount is stored on the lines (so returns and reports handle it like any line discount): each line
     * gets its share in proportion to its amount; the last line takes the rounding remainder so the shares add up
     * exactly.
     */
    private static List<PricedLine> spread(List<PricedLine> lines, BigDecimal total, BigDecimal basket, String name) {
        List<PricedLine> result = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            PricedLine l = lines.get(i);
            BigDecimal share;
            if (i == lines.size() - 1) {
                share = total.subtract(given);
            } else {
                BigDecimal lineAmount = Money.times(l.unitPrice(), l.quantity());
                share = basket.signum() == 0 ? BigDecimal.ZERO
                        : total.multiply(lineAmount).divide(basket, 2, RoundingMode.HALF_UP);
            }
            given = given.add(share);
            result.add(new PricedLine(l.product(), l.quantity(), l.basePrice(), l.unitPrice(), share, name));
        }
        return result;
    }
}
