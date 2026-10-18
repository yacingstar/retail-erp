package com.retailerp.purchasing;

/** DRAFT (editable) → ORDERED (sent to supplier) → RECEIVED (stock added). DRAFT/ORDERED → CANCELLED. */
public enum PurchaseOrderStatus {
    DRAFT, ORDERED, RECEIVED, CANCELLED
}
