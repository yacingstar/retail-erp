package com.retailerp.payables;

import java.util.UUID;

/** Application event: A supplier bill was voided (wasApproved = it had been approved, so it was in the books). Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SupplierBillVoided(UUID billId, boolean wasApproved) {
}
