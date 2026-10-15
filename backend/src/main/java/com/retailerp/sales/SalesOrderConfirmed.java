package com.retailerp.sales;

import java.util.UUID;

/** Application event: A sales order was confirmed: stock removed, invoice issued. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SalesOrderConfirmed(UUID salesOrderId) {
}
