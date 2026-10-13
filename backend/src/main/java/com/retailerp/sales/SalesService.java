package com.retailerp.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogService;
import com.retailerp.catalog.Product;
import com.retailerp.catalog.Warehouse;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.common.PartyDtos.PartyRequest;
import com.retailerp.common.PartyDtos.PartyResponse;
import com.retailerp.inventory.MovementType;
import com.retailerp.inventory.StockService;
import com.retailerp.inventory.StockService.StockKey;
import com.retailerp.invoicing.Invoice;
import com.retailerp.invoicing.InvoiceService;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.sales.SalesDtos.LineResponse;
import com.retailerp.sales.SalesDtos.SalesOrderRequest;
import com.retailerp.sales.SalesDtos.SalesOrderResponse;
import com.retailerp.sales.SalesDtos.SalesOrderSummary;
import com.retailerp.security.CurrentUser;

@Service
@Transactional
public class SalesService {

    static final String REFERENCE_TYPE = "SALES_ORDER";

    private final CustomerRepository customers;
    private final SalesOrderRepository orders;
    private final CatalogService catalog;
    private final StockService stock;
    private final InvoiceService invoices;
    private final DocumentNumberService numbers;
    private final BigDecimal cashierMaxDiscountPercent;

    public SalesService(CustomerRepository customers, SalesOrderRepository orders, CatalogService catalog, StockService stock,
            InvoiceService invoices, DocumentNumberService numbers,
            @Value("${app.pos.cashier-max-discount-percent:10}") BigDecimal cashierMaxDiscountPercent) {
        this.cashierMaxDiscountPercent = cashierMaxDiscountPercent;
        this.customers = customers;
        this.orders = orders;
        this.catalog = catalog;
        this.stock = stock;
        this.invoices = invoices;
        this.numbers = numbers;
    }

    // --- customers ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> searchCustomers(String q, Pageable pageable) {
        return PageResponse.of(customers.search(q == null ? "" : q.trim(), pageable), PartyResponse::from);
    }

    @Transactional(readOnly = true)
    public PartyResponse getCustomer(UUID id) {
        return PartyResponse.from(customer(id));
    }

    public PartyResponse createCustomer(PartyRequest request) {
        Customer customer = new Customer();
        customer.update(request);
        return PartyResponse.from(customers.saveAndFlush(customer));
    }

    public PartyResponse updateCustomer(UUID id, PartyRequest request) {
        Customer customer = customer(id);
        customer.update(request);
        customers.flush();
        return PartyResponse.from(customer);
    }

    public void deleteCustomer(UUID id) {
        customers.delete(customer(id));
        customers.flush();
    }

    private Customer customer(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    // --- sales orders -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSummary> searchOrders(SalesOrderStatus status, String q, Pageable pageable) {
        return PageResponse.of(orders.search(status, q == null ? "" : q.trim(), pageable), SalesOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getOrder(UUID id) {
        return toResponse(order(id));
    }

    public SalesOrderResponse createOrder(SalesOrderRequest request) {
        SalesOrder order = new SalesOrder(numbers.next(DocumentType.SALES_ORDER), CurrentUser.id(), null);
        apply(order, request);
        return toResponse(orders.save(order));
    }

    /**
     * Point of Sale: create and confirm in one go (stock + invoice), inside the caller's transaction, so the payment
     * recorded next is part of the same all-or-nothing unit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SalesOrder createConfirmed(SalesOrderRequest request, UUID posShiftId) {
        SalesOrder order = new SalesOrder(numbers.next(DocumentType.SALES_ORDER), CurrentUser.id(), posShiftId);
        apply(order, request);
        orders.save(order);
        confirmOrder(order);
        return order;
    }

    public SalesOrderResponse updateOrder(UUID id, SalesOrderRequest request) {
        SalesOrder order = order(id);
        apply(order, request);
        orders.flush();
        return toResponse(order);
    }

    public void deleteOrder(UUID id) {
        SalesOrder order = order(id);
        order.requireStatus(SalesOrderStatus.DRAFT, "delete");
        orders.delete(order);
    }

    /**
     * Confirming a sale, all in ONE transaction (all or nothing):
     * lock the stock rows → check availability → write SALE movements → mark confirmed → issue the invoice.
     */
    public SalesOrderResponse confirm(UUID id) {
        SalesOrder order = order(id);
        confirmOrder(order);
        return toResponse(order);
    }

    private void confirmOrder(SalesOrder order) {
        order.requireStatus(SalesOrderStatus.DRAFT, "confirm");
        Warehouse warehouse = catalog.activeWarehouse(order.getWarehouse().getId());

        // The same product may appear on several lines: check the total per product.
        Map<Product, Integer> quantities = new LinkedHashMap<>();
        for (SalesOrderLine line : order.getLines()) {
            quantities.merge(line.getProduct(), line.getQuantity(), Integer::sum);
        }
        stock.lock(quantities.keySet().stream().map(p -> new StockKey(p.getId(), warehouse.getId())).toList());
        stock.requireAvailable(quantities, warehouse);

        for (SalesOrderLine line : order.getLines()) {
            stock.removeStock(line.getProduct(), warehouse, line.getQuantity(), MovementType.SALE, REFERENCE_TYPE, order.getId(),
                    order.getNumber());
        }
        order.confirm();
        invoices.issueFor(order);
        orders.flush();
    }

    /**
     * DRAFT orders: anyone may cancel. CONFIRMED orders: managers only; the invoice is voided (fails if already paid)
     * and the stock comes back as SALE_RETURN movements. The SALE movements are never deleted.
     */
    public SalesOrderResponse cancel(UUID id, boolean canCancelConfirmed) {
        SalesOrder order = order(id);
        if (order.getStatus() == SalesOrderStatus.CONFIRMED) {
            if (!canCancelConfirmed) {
                throw new AccessDeniedException("Only managers can cancel a confirmed sale.");
            }
            invoices.voidFor(order);
            for (SalesOrderLine line : order.getLines()) {
                stock.addStock(line.getProduct(), order.getWarehouse(), line.getQuantity(), MovementType.SALE_RETURN, REFERENCE_TYPE,
                        order.getId(), "Cancelled " + order.getNumber());
            }
        }
        order.cancel();
        orders.flush();
        return toResponse(order);
    }

    private void apply(SalesOrder order, SalesOrderRequest request) {
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customer(request.customerId());
            if (!customer.isActive()) {
                throw new BusinessRuleException("Customer " + customer.getName() + " is inactive.");
            }
        }
        BigDecimal discountPercent = request.discountPercent() != null ? request.discountPercent() : BigDecimal.ZERO;
        boolean limited = !CurrentUser.isManager();
        if (limited && discountPercent.compareTo(cashierMaxDiscountPercent) > 0) {
            throw new BusinessRuleException("Cashiers can give at most " + cashierMaxDiscountPercent.stripTrailingZeros().toPlainString()
                    + "% discount. Ask a manager.");
        }
        List<SalesOrderLine> lines = request.lines().stream().map(l -> {
            Product product = catalog.product(l.productId());
            if (!product.isActive()) {
                throw new BusinessRuleException("Product " + product.getSku() + " is inactive and can't be sold.");
            }
            BigDecimal unitPrice = l.unitPrice() != null ? l.unitPrice() : product.getSalePrice();
            if (limited) {
                requireWithinCashierLimit(product, unitPrice, discountPercent);
            }
            return new SalesOrderLine(product, l.quantity(), unitPrice, product.getTaxRate(), discountPercent);
        }).toList();
        order.edit(customer, catalog.activeWarehouse(request.warehouseId()),
                request.orderDate() != null ? request.orderDate() : LocalDate.now(), request.notes(), discountPercent, lines);
    }

    /** A lowered line price plus the order discount together may not exceed the cashier limit vs. the list price. */
    private void requireWithinCashierLimit(Product product, BigDecimal unitPrice, BigDecimal discountPercent) {
        BigDecimal listPrice = product.getSalePrice();
        if (listPrice.signum() == 0) {
            return;
        }
        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal effective = unitPrice.multiply(hundred.subtract(discountPercent)).divide(hundred, 6, RoundingMode.HALF_UP);
        BigDecimal floor = listPrice.multiply(hundred.subtract(cashierMaxDiscountPercent)).divide(hundred, 6, RoundingMode.HALF_UP);
        if (effective.compareTo(floor) < 0) {
            throw new BusinessRuleException("Cashiers can give at most " + cashierMaxDiscountPercent.stripTrailingZeros().toPlainString()
                    + "% discount (" + product.getSku() + "). Ask a manager.");
        }
    }

    private SalesOrder order(UUID id) {
        return orders.findWithLines(id).orElseThrow(() -> new NotFoundException("Sales order", id));
    }

    private SalesOrderResponse toResponse(SalesOrder so) {
        Invoice invoice = so.getId() == null ? null : invoices.findFor(so.getId()).orElse(null);
        Customer c = so.getCustomer();
        return new SalesOrderResponse(so.getId(), so.getNumber(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                so.getWarehouse().getId(), so.getWarehouse().getCode(), so.getWarehouse().getName(), so.getStatus(), so.getOrderDate(),
                so.getConfirmedAt(), so.getNotes(), so.getSubtotal(), so.getDiscountPercent(), so.getDiscountTotal(), so.getTaxTotal(),
                so.getTotal(),
                so.getLines().stream().map(LineResponse::from).toList(), invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getNumber(), stock.movementsFor(REFERENCE_TYPE, so.getId()), so.getCreatedAt(),
                so.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public long countActiveCustomers() {
        return customers.countByActiveTrue();
    }
}
