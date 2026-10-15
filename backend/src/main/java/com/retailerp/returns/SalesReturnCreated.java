package com.retailerp.returns;

import java.util.UUID;

/** Application event: A return (credit note) was created. Published inside the transaction; listeners (e.g. accounting) run in it. */
public record SalesReturnCreated(UUID salesReturnId) {
}
