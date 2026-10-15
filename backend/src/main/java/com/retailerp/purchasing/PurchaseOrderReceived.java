package com.retailerp.purchasing;

import java.util.UUID;

/**
 * Published (Spring application event) when the goods of a purchase order have been received.
 * Listeners run synchronously, inside the same transaction as the receipt.
 */
public record PurchaseOrderReceived(UUID purchaseOrderId) {
}
