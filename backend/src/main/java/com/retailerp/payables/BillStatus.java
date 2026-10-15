package com.retailerp.payables;

/** DRAFT (editable, not owed yet) → OPEN (approved: we owe it) → PARTIALLY_PAID → PAID. DRAFT/OPEN without payments → VOID. */
public enum BillStatus {
    DRAFT, OPEN, PARTIALLY_PAID, PAID, VOID
}
