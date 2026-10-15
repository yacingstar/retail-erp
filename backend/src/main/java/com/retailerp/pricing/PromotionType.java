package com.retailerp.pricing;

/**
 * PERCENT_OFF: value % off matching items. AMOUNT_OFF: value off each matching unit.
 * BUY_X_GET_Y: for every buyQty + getQty units, getQty are free (buy 2 get 1 = "3 for 2").
 * ORDER_PERCENT_OFF: value % off the whole basket when it reaches minOrderAmount.
 */
public enum PromotionType {
    PERCENT_OFF, AMOUNT_OFF, BUY_X_GET_Y, ORDER_PERCENT_OFF
}
