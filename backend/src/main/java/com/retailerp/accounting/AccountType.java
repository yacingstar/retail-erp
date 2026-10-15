package com.retailerp.accounting;

/**
 * ASSET and EXPENSE accounts normally have a debit balance; LIABILITY, EQUITY and INCOME a credit balance.
 * (Debit = left column, credit = right column. Every entry has as much on the left as on the right.)
 */
public enum AccountType {
    ASSET, LIABILITY, EQUITY, INCOME, EXPENSE;

    public boolean debitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}
