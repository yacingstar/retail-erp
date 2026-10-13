package com.retailerp.sales;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
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

    public SalesService(CustomerRepository customers, SalesOrderRepository orders, CatalogService catalog, StockService stock,
            InvoiceService invoices, DocumentNumberService numbers) {
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
        SalesOrder order = new SalesOrder(numbers.next(DocumentType.SALES_ORDER), CurrentUser.id());
        apply(order, request);
        return toResponse(orders.save(order));
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
        return toResponse(order);
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
        List<SalesOrderLine> lines = request.lines().stream().map(l -> {
            Product product = catalog.product(l.productId());
            if (!product.isActive()) {
                throw new BusinessRuleException("Product " + product.getSku() + " is inactive and can't be sold.");
            }
            return new SalesOrderLine(product, l.quantity(), l.unitPrice() != null ? l.unitPrice() : product.getSalePrice(),
                    product.getTaxRate());
        }).toList();
        order.edit(customer, catalog.activeWarehouse(request.warehouseId()),
                request.orderDate() != null ? request.orderDate() : LocalDate.now(), request.notes(), lines);
    }

    private SalesOrder order(UUID id) {
        return orders.findWithLines(id).orElseThrow(() -> new NotFoundException("Sales order", id));
    }

    private SalesOrderResponse toResponse(SalesOrder so) {
        Invoice invoice = so.getId() == null ? null : invoices.findFor(so.getId()).orElse(null);
        Customer c = so.getCustomer();
        return new SalesOrderResponse(so.getId(), so.getNumber(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                so.getWarehouse().getId(), so.getWarehouse().getCode(), so.getWarehouse().getName(), so.getStatus(), so.getOrderDate(),
                so.getConfirmedAt(), so.getNotes(), so.getSubtotal(), so.getTaxTotal(), so.getTotal(),
                so.getLines().stream().map(LineResponse::from).toList(), invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getNumber(), stock.movementsFor(REFERENCE_TYPE, so.getId()), so.getCreatedAt(),
                so.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public long countActiveCustomers() {
        return customers.countByActiveTrue();
    }
}
