package com.retailerp.pricing;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.retailerp.IntegrationTest;

/** No tax on these products, so the expected numbers are easy to read. */
class PromotionsIntegrationTest extends IntegrationTest {

    private record Shop(Session admin, String warehouse, String cola, String juice, String chips) {
    }

    private Shop shop() throws Exception {
        Session admin = registerTenant("Promo Shop");
        String warehouse = postForField(admin, "/api/warehouses", Map.of("code", "W", "name", "W"), "$.id");
        String cola = product(admin, "COLA", "Drinks", "2.00");
        String juice = product(admin, "JUICE", "Drinks", "3.00");
        String chips = product(admin, "CHIPS", "Snacks", "1.50");
        for (String p : List.of(cola, juice, chips)) {
            postAs(admin, "/api/stock/adjustments", Map.of("productId", p, "warehouseId", warehouse, "quantity", 100, "note", "open"))
                    .andExpect(status().isCreated());
        }
        return new Shop(admin, warehouse, cola, juice, chips);
    }

    private String product(Session admin, String sku, String category, String price) throws Exception {
        return postForField(admin, "/api/products", Map.of("sku", sku, "name", sku, "category", category, "salePrice", price,
                "costPrice", "0.50"), "$.id");
    }

    private void promo(Session admin, Map<String, Object> body) throws Exception {
        postAs(admin, "/api/promotions", body).andExpect(status().isCreated());
    }

    private Map<String, Object> basket(Shop s, Object... productQty) {
        java.util.ArrayList<Map<String, Object>> lines = new java.util.ArrayList<>();
        for (int i = 0; i < productQty.length; i += 2) {
            lines.add(Map.of("productId", productQty[i], "quantity", productQty[i + 1]));
        }
        return new HashMap<>(Map.of("warehouseId", s.warehouse(), "lines", lines));
    }

    @Test
    void bestItemPromotionPerLine() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "20% off drinks", "type", "PERCENT_OFF", "value", "20", "scope", "CATEGORY", "category", "drinks"));
        promo(s.admin(), Map.of("name", "Cola 0.50 off", "type", "AMOUNT_OFF", "value", "0.50", "scope", "PRODUCT", "productId", s.cola()));

        // Cola 4 × 2.00 = 8.00: 20% = 1.60 vs 0.50 × 4 = 2.00 → "Cola 0.50 off" wins. Juice 2 × 3.00: 20% = 1.20. Chips: none.
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.cola(), 4, s.juice(), 2, s.chips(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].promotionName").value("Cola 0.50 off"))
                .andExpect(jsonPath("$.lines[0].promoDiscount").value(2.0))
                .andExpect(jsonPath("$.lines[1].promotionName").value("20% off drinks"))
                .andExpect(jsonPath("$.lines[1].promoDiscount").value(1.2))
                .andExpect(jsonPath("$.lines[2].promotionName").value(nullValue()))
                .andExpect(jsonPath("$.discountTotal").value(3.2))
                .andExpect(jsonPath("$.total").value(12.3)); // 15.50 − 3.20
    }

    @Test
    void buyTwoGetOneFree() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "Chips 3 for 2", "type", "BUY_X_GET_Y", "value", "0", "buyQty", 2, "getQty", 1,
                "scope", "PRODUCT", "productId", s.chips()));
        // 7 bags: two full groups of 3 → 2 free → 3.00 off 10.50
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.chips(), 7))
                .andExpect(jsonPath("$.lines[0].promoDiscount").value(3.0))
                .andExpect(jsonPath("$.total").value(7.5));
    }

    @Test
    void orderDiscountWinsOnlyWhenItSavesMoreAndIsSpreadExactly() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "5% off drinks", "type", "PERCENT_OFF", "value", "5", "scope", "CATEGORY", "category", "Drinks"));
        promo(s.admin(), Map.of("name", "10% over 20", "type", "ORDER_PERCENT_OFF", "value", "10", "minOrderAmount", "20.00"));

        // Below 20: only the item promo applies (0.10 on one cola).
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.cola(), 1))
                .andExpect(jsonPath("$.lines[0].promotionName").value("5% off drinks"))
                .andExpect(jsonPath("$.discountTotal").value(0.1));
        // 3 juice (9.00) + 7 chips (10.50) + 1 cola (2.00) = 21.50. Items save 0.45 + 0.10 = 0.55; order saves 2.15 → order wins.
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.juice(), 3, s.chips(), 7, s.cola(), 1))
                .andExpect(jsonPath("$.lines[0].promotionName").value("10% over 20"))
                .andExpect(jsonPath("$.lines[1].promotionName").value("10% over 20"))
                .andExpect(jsonPath("$.discountTotal").value(2.15))
                .andExpect(jsonPath("$.total").value(19.35));
    }

    @Test
    void couponsUnlockPromotionsAndMustBeValid() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "Welcome 15%", "type", "PERCENT_OFF", "value", "15", "couponCode", "welcome15"));
        promo(s.admin(), Map.of("name", "Old", "type", "PERCENT_OFF", "value", "50", "couponCode", "OLD50",
                "endsOn", LocalDate.now().minusDays(1).toString()));
        postAs(s.admin(), "/api/promotions", Map.of("name", "Dup", "type", "PERCENT_OFF", "value", "5", "couponCode", "WELCOME15"))
                .andExpect(status().isConflict()); // codes are unique, ignoring case

        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.juice(), 2)).andExpect(jsonPath("$.discountTotal").value(0));
        Map<String, Object> withCoupon = basket(s, s.juice(), 2);
        withCoupon.put("couponCode", "Welcome15");
        postAs(s.admin(), "/api/sales-orders/preview", withCoupon)
                .andExpect(jsonPath("$.couponCode").value("Welcome15"))
                .andExpect(jsonPath("$.discountTotal").value(0.9));
        withCoupon.put("couponCode", "OLD50");
        postAs(s.admin(), "/api/sales-orders/preview", withCoupon).andExpect(status().isConflict());
        withCoupon.put("couponCode", "NOPE");
        postAs(s.admin(), "/api/sales-orders/preview", withCoupon).andExpect(status().isConflict());
    }

    @Test
    void futurePromotionsDoNotApplyYet() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "Next week", "type", "PERCENT_OFF", "value", "50", "startsOn", LocalDate.now().plusDays(7).toString()));
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.cola(), 1)).andExpect(jsonPath("$.discountTotal").value(0));
        getAs(s.admin(), "/api/promotions").andExpect(jsonPath("$[0].runningToday").value(false));
    }

    @Test
    void priceListsAndTheCashierLimit() throws Exception {
        Shop s = shop();
        String wholesale = postForField(s.admin(), "/api/price-lists", Map.of("name", "Wholesale",
                "items", List.of(Map.of("productId", s.cola(), "price", "1.60"))), "$.id");
        String pro = postForField(s.admin(), "/api/customers", Map.of("name", "Café Central", "priceListId", wholesale), "$.id");
        getAs(s.admin(), "/api/customers/{id}", pro).andExpect(jsonPath("$.priceListId").value(wholesale));

        Map<String, Object> order = basket(s, s.cola(), 10, s.juice(), 1);
        order.put("customerId", pro);
        postAs(s.admin(), "/api/sales-orders/preview", order)
                .andExpect(jsonPath("$.lines[0].unitPrice").value(1.6))   // price list
                .andExpect(jsonPath("$.lines[1].unitPrice").value(3.0));  // not on the list: normal price

        // A cashier may go 10% below the CUSTOMER's price (1.60 → 1.44), not below that.
        Session cashier = createUser(s.admin(), "CASHIER");
        Map<String, Object> low = new HashMap<>(Map.of("warehouseId", s.warehouse(), "customerId", pro,
                "lines", List.of(Map.of("productId", s.cola(), "quantity", 1, "unitPrice", "1.40"))));
        postAs(cashier, "/api/sales-orders/preview", low).andExpect(status().isConflict());
        low.put("lines", List.of(Map.of("productId", s.cola(), "quantity", 1, "unitPrice", "1.45")));
        postAs(cashier, "/api/sales-orders/preview", low).andExpect(status().isOk());
    }

    @Test
    void manualDiscountAppliesAfterThePromotionAndPreviewMatchesTheSale() throws Exception {
        Shop s = shop();
        promo(s.admin(), Map.of("name", "20% off drinks", "type", "PERCENT_OFF", "value", "20", "scope", "CATEGORY", "category", "Drinks"));
        Map<String, Object> order = basket(s, s.juice(), 10); // 30.00 − 6.00 promo = 24.00, then 10% manual = 2.40
        order.put("discountPercent", "10");
        postAs(s.admin(), "/api/sales-orders/preview", order)
                .andExpect(jsonPath("$.lines[0].promoDiscount").value(6.0))
                .andExpect(jsonPath("$.lines[0].lineDiscount").value(8.4))
                .andExpect(jsonPath("$.total").value(21.6));
        // The preview saved nothing: the first real order still gets number SO-000001, with the same total.
        String id = postForField(s.admin(), "/api/sales-orders", order, "$.id");
        postAs(s.admin(), "/api/sales-orders/{id}/confirm", null, id)
                .andExpect(jsonPath("$.number").value("SO-000001"))
                .andExpect(jsonPath("$.total").value(21.6));
    }

    @Test
    void promotionsNeedThePlanAndManagersToEditThem() throws Exception {
        Shop s = shop();
        Session cashier = createUser(s.admin(), "CASHIER");
        getAs(cashier, "/api/promotions").andExpect(status().isOk());
        postAs(cashier, "/api/promotions", Map.of("name", "X", "type", "PERCENT_OFF", "value", "5")).andExpect(status().isForbidden());

        promo(s.admin(), Map.of("name", "Half price", "type", "PERCENT_OFF", "value", "50"));
        putAs(platformAdmin(), "/api/platform/tenants/{id}/subscription", Map.of("planCode", "STARTER", "addons", List.of()),
                s.admin().tenantId()).andExpect(status().isOk());
        // On Starter the promotion is simply not applied, and coupons are refused.
        postAs(s.admin(), "/api/sales-orders/preview", basket(s, s.cola(), 1)).andExpect(jsonPath("$.discountTotal").value(0));
        Map<String, Object> withCoupon = basket(s, s.cola(), 1);
        withCoupon.put("couponCode", "ANY");
        postAs(s.admin(), "/api/sales-orders/preview", withCoupon).andExpect(status().isForbidden());
        getAs(s.admin(), "/api/promotions").andExpect(status().isForbidden());
    }
}
