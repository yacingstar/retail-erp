package com.retailerp.invoicing;

import java.util.UUID;

/** Application event: A customer paid (part of) an invoice. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record InvoicePaymentRecorded(UUID paymentId) {
}
