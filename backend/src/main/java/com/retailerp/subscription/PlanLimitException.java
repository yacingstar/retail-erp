package com.retailerp.subscription;

/** Creating one more user/warehouse would exceed the plan's limit. Mapped to 409 with code PLAN_LIMIT_REACHED. */
public class PlanLimitException extends RuntimeException {

    private final String limit;

    public PlanLimitException(String limit, int max) {
        super("Your plan allows " + max + " active " + limit + ". Upgrade your plan to add more.");
        this.limit = limit;
    }

    public String getLimit() {
        return limit;
    }
}
