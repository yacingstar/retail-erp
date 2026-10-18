package com.retailerp.user;

/**
 * ADMIN: everything, including users. MANAGER: everything except users.
 * CASHIER: sell (sales orders, payments) and look things up.
 */
public enum Role {
    ADMIN, MANAGER, CASHIER
}
