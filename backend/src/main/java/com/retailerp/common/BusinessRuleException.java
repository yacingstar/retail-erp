package com.retailerp.common;

/** A request that is well-formed but breaks a domain rule (e.g. not enough stock). Mapped to 409 Conflict. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
