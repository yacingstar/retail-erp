package com.retailerp.subscription;

/**
 * A sellable feature area. Plans are bundles of modules; a tenant can also buy single modules as add-ons.
 * Every new feature area gets a value here, a row in plan_modules (Flyway), and {@code @RequiresModule} on its API.
 */
public enum Module {
    INVENTORY("Inventory"),
    SALES("Sales"),
    INVOICING("Invoicing"),
    PURCHASING("Purchasing"),
    POINT_OF_SALE("Point of Sale"),
    REPORTS("Reports");

    private final String displayName;

    Module(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
