package com.retailerp.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.catalog.ProductRepository;
import com.retailerp.invoicing.InvoiceService;
import com.retailerp.payables.PayablesService;
import com.retailerp.inventory.StockDtos.LowStock;
import com.retailerp.inventory.StockService;
import com.retailerp.purchasing.PurchasingService;
import com.retailerp.sales.SalesDtos.DailySales;
import com.retailerp.sales.SalesDtos.SalesOrderSummary;
import com.retailerp.sales.SalesOrderRepository;
import com.retailerp.sales.SalesOrderStatus;
import com.retailerp.sales.SalesService;
import com.retailerp.security.Access;

/** One call that gives the home screen everything it needs. Read-only, so one read-only transaction. */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize(Access.ANY)
public class DashboardController {

    private final ProductRepository products;
    private final SalesOrderRepository salesOrders;
    private final SalesService sales;
    private final PurchasingService purchasing;
    private final InvoiceService invoices;
    private final StockService stock;
    private final PayablesService payables;

    public DashboardController(ProductRepository products, SalesOrderRepository salesOrders, SalesService sales,
            PurchasingService purchasing, InvoiceService invoices, StockService stock, PayablesService payables) {
        this.payables = payables;
        this.products = products;
        this.salesOrders = salesOrders;
        this.sales = sales;
        this.purchasing = purchasing;
        this.invoices = invoices;
        this.stock = stock;
    }

    public record Dashboard(long activeProducts, long activeCustomers, long activeSuppliers, long openPurchaseOrders,
            BigDecimal salesToday, BigDecimal salesLast30Days, BigDecimal outstandingReceivables, BigDecimal outstandingPayables,
            List<LowStock> lowStock,
            List<DailySales> dailySales, List<SalesOrderSummary> recentSales) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Dashboard get() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<LowStock> low = stock.lowStock();
        return new Dashboard(
                products.countByActiveTrue(),
                sales.countActiveCustomers(),
                purchasing.countActiveSuppliers(),
                purchasing.countOpenOrders(),
                salesOrders.confirmedTotalSince(today.atStartOfDay().toInstant(ZoneOffset.UTC)),
                salesOrders.confirmedTotalSince(today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC)),
                invoices.outstandingTotal(),
                payables.outstandingTotal(),
                low.subList(0, Math.min(10, low.size())),
                salesOrders.dailySales(today.minusDays(13)),
                sales.searchOrders(SalesOrderStatus.CONFIRMED, "", PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "confirmedAt"))).content());
    }
}
