package com.retailerp.payables;

import java.util.UUID;

/** Application event: A supplier was paid. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SupplierPaymentRecorded(UUID paymentId) {
}
