package com.retailerp.returns;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.inventory.MovementType;
import com.retailerp.inventory.StockService;
import com.retailerp.invoicing.Invoice;
import com.retailerp.invoicing.InvoiceService;
import com.retailerp.invoicing.PaymentMethod;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.pos.PosShiftRepository;
import com.retailerp.pos.ShiftStatus;
import com.retailerp.returns.ReturnDtos.ReturnLineRequest;
import com.retailerp.returns.ReturnDtos.ReturnLineResponse;
import com.retailerp.returns.ReturnDtos.ReturnRequest;
import com.retailerp.returns.ReturnDtos.ReturnResponse;
import com.retailerp.returns.ReturnDtos.ReturnSummary;
import com.retailerp.returns.ReturnDtos.Returnable;
import com.retailerp.returns.ReturnDtos.ReturnableLine;
import com.retailerp.returns.ReturnDtos.Returned;
import com.retailerp.sales.SalesOrder;
import com.retailerp.sales.SalesOrderLine;
import com.retailerp.sales.SalesOrderRepository;
import com.retailerp.sales.SalesOrderStatus;
import com.retailerp.sales.StoreCreditService;
import com.retailerp.security.CurrentUser;

/**
 * A return (credit note), all in one transaction:
 * lock the invoice → check returnable quantities → compute the refund per line → restock what's resellable →
 * credit the invoice → give the money back (cash/card) or store credit if the customer had already paid.
 */
@Service
@Transactional
public class ReturnService {

    static final String REFERENCE_TYPE = "SALES_RETURN";

    private final SalesReturnRepository returns;
    private final SalesOrderRepository salesOrders;
    private final InvoiceService invoices;
    private final StockService stock;
    private final StoreCreditService storeCredit;
    private final DocumentNumberService numbers;
    private final PosShiftRepository shifts;
    private final ApplicationEventPublisher events;

    public ReturnService(SalesReturnRepository returns, SalesOrderRepository salesOrders, InvoiceService invoices, StockService stock,
            StoreCreditService storeCredit, DocumentNumberService numbers, PosShiftRepository shifts, ApplicationEventPublisher events) {
        this.events = events;
        this.returns = returns;
        this.salesOrders = salesOrders;
        this.invoices = invoices;
        this.stock = stock;
        this.storeCredit = storeCredit;
        this.numbers = numbers;
        this.shifts = shifts;
    }

    public ReturnResponse create(ReturnRequest request) {
        SalesOrder order = confirmedOrder(request.salesOrderId());
        // Lock before reading what was already returned: two returns of the same sale at the same time could
        // otherwise both pass the quantity check.
        Invoice invoice = invoices.lockFor(order.getId());
        Map<UUID, SalesOrderLine> orderLines = order.getLines().stream().collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
        Map<UUID, Returned> already = alreadyReturned(order.getId());

        SalesReturn ret = new SalesReturn(numbers.next(DocumentType.CREDIT_NOTE), order, invoice, request.reason(), CurrentUser.id());
        Set<UUID> seen = new HashSet<>();
        for (ReturnLineRequest r : request.lines()) {
            SalesOrderLine line = orderLines.get(r.salesOrderLineId());
            if (line == null) {
                throw new IllegalArgumentException("Line " + r.salesOrderLineId() + " is not part of sale " + order.getNumber() + ".");
            }
            if (!seen.add(line.getId())) {
                throw new IllegalArgumentException("Each sale line can appear only once in a return.");
            }
            ret.addLine(returnLine(line, r.quantity(), r.restock(), already.get(line.getId())));
        }

        // Credit the invoice, then see whether money is due back (only if the customer had paid more than it's now worth).
        invoice.applyCredit(ret.getTotal());
        BigDecimal refund = invoice.refundDue();
        if (refund.signum() > 0) {
            PaymentMethod method = request.refundMethod();
            if (method == null) {
                throw new BusinessRuleException("This sale was paid: choose how to refund " + refund + " (cash, card or store credit).");
            }
            if (method != PaymentMethod.CASH && method != PaymentMethod.CARD && method != PaymentMethod.STORE_CREDIT) {
                throw new IllegalArgumentException("Refunds are made in cash, by card or as store credit.");
            }
            if (method == PaymentMethod.STORE_CREDIT && order.getCustomer() == null) {
                throw new BusinessRuleException("Store credit needs a named customer; this was a walk-in sale.");
            }
            // Cash given back at the till comes out of the cashier's drawer: link it to their open shift.
            UUID shiftId = method == PaymentMethod.CASH
                    ? shifts.findFirstByOpenedByAndStatus(CurrentUser.id(), ShiftStatus.OPEN).map(s -> s.getId()).orElse(null)
                    : null;
            ret.refund(refund, method, shiftId);
            invoice.recordRefund(refund);
        }

        returns.save(ret); // last change to the immutable document: from here on it's never updated
        for (SalesReturnLine line : ret.getLines()) {
            if (line.isRestock()) {
                stock.addStock(line.getProduct(), ret.getWarehouse(), line.getQuantity(), MovementType.SALE_RETURN, REFERENCE_TYPE, ret.getId(),
                        ret.getNumber());
            }
        }
        if (ret.getRefundMethod() == PaymentMethod.STORE_CREDIT) {
            storeCredit.give(order.getCustomer(), refund, ret.getId(), "Return " + ret.getNumber());
        }
        returns.flush();
        events.publishEvent(new SalesReturnCreated(ret.getId()));
        return toResponse(ret);
    }

    /**
     * Proportional share of the original line. When the LAST remaining units are returned, use "original minus
     * already returned" instead, so rounding never leaves (or over-refunds) a cent.
     */
    private static SalesReturnLine returnLine(SalesOrderLine line, int quantity, boolean restock, Returned already) {
        long returned = already == null ? 0 : already.quantity();
        long returnable = line.getQuantity() - returned;
        if (quantity > returnable) {
            throw new BusinessRuleException("Only " + returnable + " × " + line.getProduct().getSku() + " can still be returned.");
        }
        if (quantity == returnable) {
            return new SalesReturnLine(line, quantity, restock,
                    line.getLineSubtotal().subtract(already == null ? BigDecimal.ZERO : already.subtotal()),
                    line.getLineDiscount().subtract(already == null ? BigDecimal.ZERO : already.discount()),
                    line.getLineTax().subtract(already == null ? BigDecimal.ZERO : already.tax()));
        }
        return new SalesReturnLine(line, quantity, restock, share(line.getLineSubtotal(), quantity, line.getQuantity()),
                share(line.getLineDiscount(), quantity, line.getQuantity()), share(line.getLineTax(), quantity, line.getQuantity()));
    }

    private static BigDecimal share(BigDecimal amount, int part, int whole) {
        return amount.multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Returnable returnable(UUID salesOrderId) {
        SalesOrder order = confirmedOrder(salesOrderId);
        Invoice invoice = invoices.findFor(salesOrderId).orElseThrow(() -> new NotFoundException("Invoice for sales order", salesOrderId));
        Map<UUID, Returned> already = alreadyReturned(salesOrderId);
        List<ReturnableLine> lines = order.getLines().stream().map(l -> {
            long returned = already.containsKey(l.getId()) ? already.get(l.getId()).quantity() : 0;
            return new ReturnableLine(l.getId(), l.getLineNo(), l.getProduct().getId(), l.getProduct().getSku(), l.getProduct().getName(),
                    l.getQuantity(), returned, l.getQuantity() - returned, l.getUnitPrice(),
                    l.getLineTotal().divide(BigDecimal.valueOf(l.getQuantity()), 2, RoundingMode.HALF_UP));
        }).toList();
        return new Returnable(order.getId(), order.getNumber(), order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getCustomer() == null ? null : order.getCustomer().getName(), invoice.getNumber(), invoice.getTotal(), invoice.getAmountPaid(),
                lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnSummary> search(String q, Pageable pageable) {
        return PageResponse.of(returns.search(q == null ? "" : q.trim(), pageable), r -> new ReturnSummary(r.getId(), r.getNumber(),
                r.getReturnDate(), r.getSalesOrder().getId(), r.getSalesOrder().getNumber(),
                r.getCustomer() == null ? null : r.getCustomer().getName(), r.getTotal(), r.getRefundAmount(), r.getRefundMethod()));
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(UUID id) {
        return toResponse(returns.findWithLines(id).orElseThrow(() -> new NotFoundException("Return", id)));
    }

    @Transactional(readOnly = true)
    public List<ReturnSummary> forSalesOrder(UUID salesOrderId) {
        return returns.findBySalesOrderIdOrderByCreatedAtAsc(salesOrderId).stream().map(r -> new ReturnSummary(r.getId(), r.getNumber(),
                r.getReturnDate(), salesOrderId, null, null, r.getTotal(), r.getRefundAmount(), r.getRefundMethod())).toList();
    }

    private SalesOrder confirmedOrder(UUID id) {
        SalesOrder order = salesOrders.findWithLines(id).orElseThrow(() -> new NotFoundException("Sales order", id));
        if (order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new BusinessRuleException("Only confirmed sales can be returned (" + order.getNumber() + " is " + order.getStatus() + ").");
        }
        return order;
    }

    private Map<UUID, Returned> alreadyReturned(UUID orderId) {
        return returns.returnedPerLine(orderId).stream().collect(Collectors.toMap(Returned::salesOrderLineId, Function.identity()));
    }

    private ReturnResponse toResponse(SalesReturn r) {
        List<ReturnLineResponse> lines = r.getLines().stream().map(l -> new ReturnLineResponse(l.getProduct().getId(), l.getProduct().getSku(),
                l.getProduct().getName(), l.getQuantity(), l.isRestock(), l.getLineSubtotal(), l.getLineDiscount(), l.getLineTax(),
                l.getLineTotal())).toList();
        return new ReturnResponse(r.getId(), r.getNumber(), r.getReturnDate(), r.getSalesOrder().getId(), r.getSalesOrder().getNumber(),
                r.getInvoice().getId(), r.getInvoice().getNumber(), r.getCustomer() == null ? null : r.getCustomer().getId(),
                r.getCustomer() == null ? null : r.getCustomer().getName(), r.getWarehouse().getCode(), r.getReason(), r.getSubtotal(),
                r.getDiscountTotal(), r.getTaxTotal(), r.getTotal(), r.getRefundAmount(), r.getRefundMethod(), lines, r.getCreatedAt());
    }
}
