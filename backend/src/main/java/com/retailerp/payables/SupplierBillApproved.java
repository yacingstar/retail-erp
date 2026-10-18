package com.retailerp.payables;

import java.util.UUID;

/** Application event: A supplier bill was approved: it is now owed. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SupplierBillApproved(UUID billId) {
}
