package com.retailerp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is always BigDecimal with 2 decimals, rounded half-up. Never use double for money. */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal times(BigDecimal unitPrice, int quantity) {
        return round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal ratePercent) {
        return round(amount.multiply(ratePercent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }
}
