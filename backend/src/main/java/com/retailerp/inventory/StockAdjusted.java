package com.retailerp.inventory;

import java.util.UUID;

/** Application event: A manual stock adjustment (found / lost / damaged goods). Published inside the transaction; listeners (e.g. accounting) run in it. */
public record StockAdjusted(UUID movementId) {
}
