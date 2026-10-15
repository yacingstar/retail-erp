package com.retailerp.inventory;

/** Why stock changed. The sign of the quantity says the direction (+ in, - out). */
public enum MovementType {
    PURCHASE_RECEIPT, SALE, SALE_RETURN, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT
}
