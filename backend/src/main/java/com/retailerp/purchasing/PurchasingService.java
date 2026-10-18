package com.retailerp.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogService;
import com.retailerp.catalog.Product;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.common.PartyDtos.PartyRequest;
import com.retailerp.common.PartyDtos.PartyResponse;
import com.retailerp.inventory.MovementType;
import com.retailerp.inventory.StockService;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.purchasing.PurchasingDtos.LineResponse;
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.retailerp.purchasing.PurchasingDtos.PurchaseOrderSummary;
import com.retailerp.security.CurrentUser;

@Service
@Transactional
public class PurchasingService {

    static final String REFERENCE_TYPE = "PURCHASE_ORDER";

    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final CatalogService catalog;
    private final StockService stock;
    private final DocumentNumberService numbers;
    private final ApplicationEventPublisher events;

    public PurchasingService(SupplierRepository suppliers, PurchaseOrderRepository orders, CatalogService catalog, StockService stock,
            DocumentNumberService numbers, ApplicationEventPublisher events) {
        this.events = events;
        this.suppliers = suppliers;
        this.orders = orders;
        this.catalog = catalog;
        this.stock = stock;
        this.numbers = numbers;
    }

    // --- suppliers ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> searchSuppliers(String q, Pageable pageable) {
        return PageResponse.of(suppliers.search(q == null ? "" : q.trim(), pageable), PartyResponse::from);
    }

    @Transactional(readOnly = true)
    public PartyResponse getSupplier(UUID id) {
        return PartyResponse.from(supplier(id));
    }

    public PartyResponse createSupplier(PartyRequest request) {
        Supplier supplier = new Supplier();
        supplier.update(request);
        return PartyResponse.from(suppliers.saveAndFlush(supplier));
    }

    public PartyResponse updateSupplier(UUID id, PartyRequest request) {
        Supplier supplier = supplier(id);
        supplier.update(request);
        suppliers.flush();
        return PartyResponse.from(supplier);
    }

    public void deleteSupplier(UUID id) {
        suppliers.delete(supplier(id));
        suppliers.flush();
    }

    private Supplier supplier(UUID id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    // --- purchase orders --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderSummary> searchOrders(PurchaseOrderStatus status, String q, Pageable pageable) {
        return PageResponse.of(orders.search(status, q == null ? "" : q.trim(), pageable), PurchaseOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getOrder(UUID id) {
        return toResponse(order(id));
    }

    public PurchaseOrderResponse createOrder(PurchaseOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder(numbers.next(DocumentType.PURCHASE_ORDER), CurrentUser.id());
        apply(order, request);
        return toResponse(orders.save(order));
    }

    public PurchaseOrderResponse updateOrder(UUID id, PurchaseOrderRequest request) {
        PurchaseOrder order = order(id);
        apply(order, request);
        orders.flush();
        return toResponse(order);
    }

    public void deleteOrder(UUID id) {
        PurchaseOrder order = order(id);
        order.requireStatus(PurchaseOrderStatus.DRAFT, "delete");
        orders.delete(order);
    }

    public PurchaseOrderResponse markOrdered(UUID id) {
        PurchaseOrder order = order(id);
        order.markOrdered();
        return toResponse(order);
    }

    /** Receiving = one PURCHASE_RECEIPT stock movement per line, plus the line cost becomes the product's cost. */
    public PurchaseOrderResponse receive(UUID id) {
        PurchaseOrder order = order(id);
        order.markReceived();
        catalog.activeWarehouse(order.getWarehouse().getId());
        for (PurchaseOrderLine line : order.getLines()) {
            stock.addStock(line.getProduct(), order.getWarehouse(), line.getQuantity(), MovementType.PURCHASE_RECEIPT, REFERENCE_TYPE,
                    order.getId(), order.getNumber());
            line.getProduct().updateCostPrice(line.getUnitCost());
        }
        orders.flush();
        // Tell whoever is interested (e.g. supplier bills) without knowing who they are.
        events.publishEvent(new PurchaseOrderReceived(order.getId()));
        return toResponse(order);
    }

    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder order = order(id);
        order.cancel();
        return toResponse(order);
    }

    private void apply(PurchaseOrder order, PurchaseOrderRequest request) {
        Supplier supplier = supplier(request.supplierId());
        if (!supplier.isActive()) {
            throw new BusinessRuleException("Supplier " + supplier.getName() + " is inactive.");
        }
        List<PurchaseOrderLine> lines = request.lines().stream().map(l -> {
            Product product = catalog.product(l.productId());
            return new PurchaseOrderLine(product, l.quantity(), l.unitCost() != null ? l.unitCost() : product.getCostPrice());
        }).toList();
        order.edit(supplier, catalog.activeWarehouse(request.warehouseId()),
                request.orderDate() != null ? request.orderDate() : LocalDate.now(), request.expectedDate(), request.notes(), lines);
    }

    private PurchaseOrder order(UUID id) {
        return orders.findWithLines(id).orElseThrow(() -> new NotFoundException("Purchase order", id));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        List<LineResponse> lines = po.getLines().stream()
                .map(l -> new LineResponse(l.getLineNo(), l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
                        l.getQuantity(), l.getUnitCost(), l.getLineTotal()))
                .toList();
        return new PurchaseOrderResponse(po.getId(), po.getNumber(), po.getSupplier().getId(), po.getSupplier().getName(),
                po.getWarehouse().getId(), po.getWarehouse().getCode(), po.getWarehouse().getName(), po.getStatus(), po.getOrderDate(),
                po.getExpectedDate(), po.getReceivedAt(), po.getNotes(), po.getTotal(), lines,
                po.getId() == null ? List.of() : stock.movementsFor(REFERENCE_TYPE, po.getId()), po.getCreatedAt(), po.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public long countOpenOrders() {
        return orders.countByStatusIn(Set.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.ORDERED));
    }

    @Transactional(readOnly = true)
    public long countActiveSuppliers() {
        return suppliers.countByActiveTrue();
    }
}
