package com.retailerp.reports;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.reports.ReportDtos.Period;
import com.retailerp.reports.ReportDtos.ProductReport;
import com.retailerp.reports.ReportDtos.SalesReport;
import com.retailerp.reports.ReportDtos.StockValueReport;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

/** Reports show revenue, costs and profit, so they're for managers and admins only. Dates are ISO (2026-10-31). */
@RestController
@RequestMapping("/api/reports")
@RequiresModule(Module.REPORTS)
@PreAuthorize(Access.MANAGE)
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/sales")
    public SalesReport sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") Period groupBy) {
        return reports.sales(from, to, groupBy);
    }

    /** sortBy: REVENUE (default), QUANTITY (best sellers) or PROFIT. */
    @GetMapping("/products")
    public ProductReport products(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "REVENUE") String sortBy) {
        return reports.products(from, to, sortBy);
    }

    @GetMapping("/stock-value")
    public StockValueReport stockValue(@RequestParam(required = false) UUID warehouseId) {
        return reports.stockValue(warehouseId);
    }
}
