package com.retailerp.sales;

import java.util.UUID;

/** Application event: A CONFIRMED sales order was cancelled: stock returned, invoice voided. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SalesOrderCancelled(UUID salesOrderId) {
}
