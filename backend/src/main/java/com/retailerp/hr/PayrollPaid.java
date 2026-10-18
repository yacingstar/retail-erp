package com.retailerp.hr;

import java.util.UUID;

/** Published when the net salaries of a run are paid out. */
public record PayrollPaid(UUID runId) {
}
