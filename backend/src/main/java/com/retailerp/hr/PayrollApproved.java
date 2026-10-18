package com.retailerp.hr;

import java.util.UUID;

/** Published when a payroll run is approved: accounting records the salary expense and what is owed. */
public record PayrollApproved(UUID runId) {
}
