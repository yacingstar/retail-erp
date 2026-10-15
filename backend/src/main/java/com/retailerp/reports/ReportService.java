package com.retailerp.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.reports.ReportDtos.GroupTotal;
import com.retailerp.reports.ReportDtos.Period;
import com.retailerp.reports.ReportDtos.ProductReport;
import com.retailerp.reports.ReportDtos.ProductRow;
import com.retailerp.reports.ReportDtos.SalesReport;
import com.retailerp.reports.ReportDtos.SalesRow;
import com.retailerp.reports.ReportDtos.StockValueReport;
import com.retailerp.reports.ReportDtos.StockValueRow;

/**
 * Reports are read-only aggregations, so they use plain SQL (JdbcTemplate) instead of JPA: GROUP BY, date_trunc and
 * SUM are what SQL is best at, and nothing needs to be loaded as entities.
 * <p>
 * No "WHERE tenant_id = ?" anywhere: Row-Level Security limits every query to the current tenant. That's the
 * backup layer doing the main job here, which is exactly why it must be reliable (see TenantIsolationTest).
 * Only CONFIRMED sales count; cancelled ones are excluded.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_DAYS = 3 * 366;

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SalesReport sales(LocalDate from, LocalDate to, Period groupBy) {
        validate(from, to);
        String unit = groupBy.name().toLowerCase(); // 'day' | 'week' | 'month' (an enum, never user text: safe in SQL)
        List<SalesRow> rows = jdbc.query("""
                SELECT date_trunc('%s', order_date)::date AS period,
                       count(*) AS orders,
                       sum(subtotal) AS gross, sum(discount_total) AS discounts, sum(tax_total) AS tax, sum(total) AS total
                FROM sales_orders
                WHERE status = 'CONFIRMED' AND order_date BETWEEN ? AND ?
                GROUP BY 1 ORDER BY 1
                """.formatted(unit), (rs, i) -> salesRow(rs.getObject("period", LocalDate.class), rs), from, to);
        long orders = rows.stream().mapToLong(SalesRow::orders).sum();
        SalesRow totals = salesRow(null, orders, sum(rows, SalesRow::grossSales), sum(rows, SalesRow::discounts), sum(rows, SalesRow::tax),
                sum(rows, SalesRow::total));
        return new SalesReport(from, to, groupBy, rows, totals);
    }

    /** Products sold in the period with revenue, cost of goods sold and profit. */
    public ProductReport products(LocalDate from, LocalDate to, String sortBy) {
        validate(from, to);
        List<ProductRow> rows = jdbc.query("""
                SELECT p.id, p.sku, p.name, p.category,
                       sum(l.quantity) AS quantity,
                       sum(l.line_subtotal - l.line_discount) AS net,
                       sum(l.unit_cost * l.quantity) AS cost
                FROM sales_order_lines l
                JOIN sales_orders o ON o.id = l.sales_order_id
                JOIN products p ON p.id = l.product_id
                WHERE o.status = 'CONFIRMED' AND o.order_date BETWEEN ? AND ?
                GROUP BY p.id, p.sku, p.name, p.category
                """, (rs, i) -> productRow(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getString("category"), rs.getLong("quantity"), rs.getBigDecimal("net"), rs.getBigDecimal("cost")), from, to);
        Comparator<ProductRow> order = switch (sortBy == null ? "REVENUE" : sortBy.toUpperCase()) {
            case "QUANTITY" -> Comparator.comparingLong(ProductRow::quantity).reversed();
            case "PROFIT" -> Comparator.comparing(ProductRow::grossProfit).reversed();
            default -> Comparator.comparing(ProductRow::netSales).reversed();
        };
        rows = rows.stream().sorted(order.thenComparing(ProductRow::name)).toList();
        ProductRow totals = productRow(null, null, "Total", null, rows.stream().mapToLong(ProductRow::quantity).sum(),
                sum(rows, ProductRow::netSales), sum(rows, ProductRow::cost));
        return new ProductReport(from, to, rows, totals);
    }

    /** What the stock on hand is worth right now, at the current cost price. */
    public StockValueReport stockValue(UUID warehouseId) {
        List<StockValueRow> rows = jdbc.query("""
                SELECT p.id, p.sku, p.name, p.category, w.id AS warehouse_id, w.code, sum(m.quantity) AS quantity, p.cost_price
                FROM stock_movements m
                JOIN products p ON p.id = m.product_id
                JOIN warehouses w ON w.id = m.warehouse_id
                WHERE (?::uuid IS NULL OR w.id = ?::uuid)
                GROUP BY p.id, p.sku, p.name, p.category, w.id, w.code, p.cost_price
                HAVING sum(m.quantity) <> 0
                ORDER BY p.name, w.code
                """, (rs, i) -> {
            long qty = rs.getLong("quantity");
            BigDecimal cost = rs.getBigDecimal("cost_price");
            return new StockValueRow(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"), rs.getString("category"),
                    rs.getObject("warehouse_id", UUID.class), rs.getString("code"), qty, cost,
                    cost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
        }, warehouseId, warehouseId);
        return new StockValueReport(rows, group(rows, StockValueRow::warehouseCode), group(rows, r -> r.category() == null ? "Uncategorized" : r.category()),
                sum(rows, StockValueRow::value));
    }

    private static List<GroupTotal> group(List<StockValueRow> rows, Function<StockValueRow, String> key) {
        Map<String, GroupTotal> groups = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparing(key)).forEach(r -> groups.merge(key.apply(r), new GroupTotal(key.apply(r), r.quantity(), r.value()),
                (a, b) -> new GroupTotal(a.name(), a.quantity() + b.quantity(), a.value().add(b.value()))));
        return List.copyOf(groups.values());
    }

    private static SalesRow salesRow(LocalDate period, ResultSet rs) throws SQLException {
        return salesRow(period, rs.getLong("orders"), rs.getBigDecimal("gross"), rs.getBigDecimal("discounts"), rs.getBigDecimal("tax"),
                rs.getBigDecimal("total"));
    }

    private static SalesRow salesRow(LocalDate period, long orders, BigDecimal gross, BigDecimal discounts, BigDecimal tax, BigDecimal total) {
        BigDecimal average = orders == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
        return new SalesRow(period, orders, gross, discounts, gross.subtract(discounts), tax, total, average);
    }

    private static ProductRow productRow(UUID id, String sku, String name, String category, long quantity, BigDecimal net, BigDecimal cost) {
        BigDecimal profit = net.subtract(cost);
        BigDecimal margin = net.signum() == 0 ? BigDecimal.ZERO
                : profit.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP);
        return new ProductRow(id, sku, name, category, quantity, net, cost.setScale(2, RoundingMode.HALF_UP), profit.setScale(2, RoundingMode.HALF_UP),
                margin);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be on or before 'to'.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("The period can be at most 3 years.");
        }
    }
}
