package com.retailerp.sales;

/** DRAFT (editable, no stock effect) → CONFIRMED (stock removed, invoice issued) → CANCELLED (stock returned). */
public enum SalesOrderStatus {
    DRAFT, CONFIRMED, CANCELLED
}
