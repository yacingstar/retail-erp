package com.retailerp.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public enum Period {
        DAY, WEEK, MONTH
    }

    /** Net sales = subtotal − discounts (excluding tax). Total = net + tax (what customers paid). */
    public record SalesRow(LocalDate period, long orders, BigDecimal grossSales, BigDecimal discounts, BigDecimal netSales,
            BigDecimal tax, BigDecimal total, BigDecimal averageSale) {
    }

    public record SalesReport(LocalDate from, LocalDate to, Period groupBy, List<SalesRow> rows, SalesRow totals) {
    }

    public record ProductRow(UUID productId, String sku, String name, String category, long quantity, BigDecimal netSales,
            BigDecimal cost, BigDecimal grossProfit, BigDecimal marginPercent) {
    }

    public record ProductReport(LocalDate from, LocalDate to, List<ProductRow> rows, ProductRow totals) {
    }

    public record StockValueRow(UUID productId, String sku, String name, String category, UUID warehouseId, String warehouseCode,
            long quantity, BigDecimal unitCost, BigDecimal value) {
    }

    public record GroupTotal(String name, long quantity, BigDecimal value) {
    }

    public record StockValueReport(List<StockValueRow> rows, List<GroupTotal> byWarehouse, List<GroupTotal> byCategory, BigDecimal totalValue) {
    }
}
