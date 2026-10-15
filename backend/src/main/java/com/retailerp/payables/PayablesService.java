package com.retailerp.payables;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.Money;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.payables.PayablesDtos.BillLineResponse;
import com.retailerp.payables.PayablesDtos.BillRequest;
import com.retailerp.payables.PayablesDtos.BillResponse;
import com.retailerp.payables.PayablesDtos.BillSummary;
import com.retailerp.payables.PayablesDtos.StatementEntry;
import com.retailerp.payables.PayablesDtos.SupplierPaymentRequest;
import com.retailerp.payables.PayablesDtos.SupplierPaymentResponse;
import com.retailerp.payables.PayablesDtos.SupplierStatement;
import com.retailerp.purchasing.PurchaseOrder;
import com.retailerp.purchasing.PurchaseOrderReceived;
import com.retailerp.purchasing.PurchaseOrderRepository;
import com.retailerp.purchasing.PurchaseOrderLine;
import com.retailerp.purchasing.Supplier;
import com.retailerp.purchasing.SupplierRepository;
import com.retailerp.security.CurrentUser;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.SubscriptionService;

/**
 * What we owe suppliers. Bills start as DRAFT (to check against the supplier's paper bill), are approved (now owed),
 * then paid in one or more payments. The supplier balance is derived: approved bills − payments.
 */
@Service
@Transactional
public class PayablesService {

    private final SupplierBillRepository bills;
    private final SupplierPaymentRepository payments;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;
    private final DocumentNumberService numbers;
    private final SubscriptionService subscriptions;
    private final ApplicationEventPublisher events;

    public PayablesService(SupplierBillRepository bills, SupplierPaymentRepository payments, SupplierRepository suppliers,
            PurchaseOrderRepository purchaseOrders, DocumentNumberService numbers, SubscriptionService subscriptions,
            ApplicationEventPublisher events) {
        this.events = events;
        this.bills = bills;
        this.payments = payments;
        this.suppliers = suppliers;
        this.purchaseOrders = purchaseOrders;
        this.numbers = numbers;
        this.subscriptions = subscriptions;
    }

    /**
     * {@code @EventListener}: Spring calls this when PurchasingService publishes PurchaseOrderReceived. It runs in the
     * same thread and transaction, so if creating the bill fails, the receipt rolls back too. Purchasing doesn't know
     * this module exists (no dependency from purchasing to payables).
     */
    @EventListener
    public void onPurchaseOrderReceived(PurchaseOrderReceived event) {
        if (!subscriptions.current().has(Module.SUPPLIER_BILLS) || bills.findByPurchaseOrderId(event.purchaseOrderId()).isPresent()) {
            return;
        }
        PurchaseOrder po = purchaseOrders.findWithLines(event.purchaseOrderId()).orElseThrow();
        SupplierBill bill = new SupplierBill(numbers.next(DocumentType.SUPPLIER_BILL), po, CurrentUser.id());
        List<SupplierBillLine> lines = new ArrayList<>();
        for (PurchaseOrderLine l : po.getLines()) {
            lines.add(new SupplierBillLine(l.getProduct().getSku() + " · " + l.getProduct().getName(), BigDecimal.valueOf(l.getQuantity()),
                    l.getUnitCost(), l.getProduct().getTaxRate()));
        }
        bill.edit(po.getSupplier(), null, LocalDate.now(), null, "Goods received on " + po.getNumber(), null, lines);
        bills.save(bill);
    }

    public BillResponse create(BillRequest request) {
        SupplierBill bill = new SupplierBill(numbers.next(DocumentType.SUPPLIER_BILL), null, CurrentUser.id());
        apply(bill, request);
        return toResponse(bills.save(bill));
    }

    public BillResponse update(UUID id, BillRequest request) {
        SupplierBill bill = bill(id);
        apply(bill, request);
        bills.flush();
        return toResponse(bill);
    }

    public BillResponse approve(UUID id) {
        SupplierBill bill = bill(id);
        bill.approve();
        bills.flush();
        events.publishEvent(new SupplierBillApproved(bill.getId()));
        return toResponse(bill);
    }

    public BillResponse voidBill(UUID id) {
        SupplierBill bill = bill(id);
        boolean wasApproved = bill.isApproved();
        bill.voidBill();
        bills.flush();
        events.publishEvent(new SupplierBillVoided(bill.getId(), wasApproved));
        return toResponse(bill);
    }

    public BillResponse pay(UUID id, SupplierPaymentRequest request) {
        SupplierBill bill = bills.findForUpdate(id).orElseThrow(() -> new NotFoundException("Bill", id));
        BigDecimal amount = Money.round(request.amount());
        bill.registerPayment(amount);
        SupplierPayment payment = payments.save(new SupplierPayment(bill, amount, request.method(), request.reference(),
                request.paidAt() != null ? request.paidAt() : Instant.now(), CurrentUser.id()));
        bills.flush();
        events.publishEvent(new SupplierPaymentRecorded(payment.getId()));
        return toResponse(bills.findWithLines(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<BillSummary> search(BillStatus status, UUID supplierId, String q, Pageable pageable) {
        return PageResponse.of(bills.search(status, supplierId, q == null ? "" : q.trim(), pageable), b -> new BillSummary(b.getId(),
                b.getNumber(), b.getSupplierReference(), b.getSupplier().getId(), b.getSupplier().getName(), b.getStatus(), b.getBillDate(),
                b.getDueDate(), b.getTotal(), b.getAmountPaid(), b.balanceDue(),
                b.getPurchaseOrder() == null ? null : b.getPurchaseOrder().getNumber()));
    }

    @Transactional(readOnly = true)
    public BillResponse get(UUID id) {
        return toResponse(bill(id));
    }

    @Transactional(readOnly = true)
    public BillResponse forPurchaseOrder(UUID purchaseOrderId) {
        return bills.findByPurchaseOrderId(purchaseOrderId).map(b -> toResponse(bill(b.getId())))
                .orElseThrow(() -> new NotFoundException("Bill for purchase order", purchaseOrderId));
    }

    /** Approved bills (+) and payments (−) in date order, with a running balance: what we owe the supplier. */
    @Transactional(readOnly = true)
    public SupplierStatement statement(UUID supplierId) {
        Supplier supplier = suppliers.findById(supplierId).orElseThrow(() -> new NotFoundException("Supplier", supplierId));
        record Row(LocalDate date, Instant at, String type, UUID billId, String document, String reference, BigDecimal amount) {
        }
        List<Row> rows = new ArrayList<>();
        for (SupplierBill b : bills.approvedFor(supplierId)) {
            rows.add(new Row(b.getBillDate(), b.getApprovedAt(), "BILL", b.getId(), b.getNumber(), b.getSupplierReference(), b.getTotal()));
        }
        for (SupplierPayment p : payments.forSupplier(supplierId)) {
            rows.add(new Row(p.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate(), p.getPaidAt(), "PAYMENT", p.getBill().getId(),
                    p.getBill().getNumber(), p.getReference(), p.getAmount().negate()));
        }
        rows.sort(Comparator.comparing(Row::date).thenComparing(r -> r.at() == null ? Instant.EPOCH : r.at()));
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        List<StatementEntry> entries = new ArrayList<>();
        for (Row r : rows) {
            balance = balance.add(r.amount());
            if (r.amount().signum() > 0) {
                billed = billed.add(r.amount());
            } else {
                paid = paid.add(r.amount().negate());
            }
            entries.add(new StatementEntry(r.date(), r.type(), r.billId(), r.document(), r.reference(), r.amount(), balance));
        }
        return new SupplierStatement(supplierId, supplier.getName(), billed, paid, balance, entries);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstandingTotal() {
        return bills.outstandingTotal();
    }

    private void apply(SupplierBill bill, BillRequest r) {
        Supplier supplier = suppliers.findById(r.supplierId()).orElseThrow(() -> new NotFoundException("Supplier", r.supplierId()));
        List<SupplierBillLine> lines = r.lines().stream()
                .map(l -> new SupplierBillLine(l.description().trim(), l.quantity(), l.unitPrice(), l.taxRate())).toList();
        if (r.dueDate() != null && r.billDate() != null && r.dueDate().isBefore(r.billDate())) {
            throw new IllegalArgumentException("The due date can't be before the bill date.");
        }
        String expenseAccount = bill.getPurchaseOrder() != null ? null : (r.expenseAccount() == null ? "607" : r.expenseAccount());
        bill.edit(supplier, blank(r.supplierReference()), r.billDate() != null ? r.billDate() : LocalDate.now(), r.dueDate(), r.description(),
                expenseAccount, lines);
    }

    private SupplierBill bill(UUID id) {
        return bills.findWithLines(id).orElseThrow(() -> new NotFoundException("Bill", id));
    }

    private BillResponse toResponse(SupplierBill b) {
        return new BillResponse(b.getId(), b.getNumber(), b.getSupplierReference(), b.getSupplier().getId(), b.getSupplier().getName(),
                b.getStatus(), b.getBillDate(), b.getDueDate(), b.getDescription(), b.getExpenseAccount(), b.getSubtotal(), b.getTaxTotal(),
                b.getTotal(), b.getAmountPaid(), b.balanceDue(),
                b.getPurchaseOrder() == null ? null : b.getPurchaseOrder().getId(),
                b.getPurchaseOrder() == null ? null : b.getPurchaseOrder().getNumber(), b.getApprovedAt(),
                b.getLines().stream().map(l -> new BillLineResponse(l.getLineNo(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                        l.getLineTotal(), l.getTaxRate(), l.getLineTax())).toList(),
                b.getId() == null ? List.of() : payments.findByBillIdOrderByPaidAtAsc(b.getId()).stream()
                        .map(p -> new SupplierPaymentResponse(p.getId(), p.getAmount(), p.getMethod(), p.getReference(), p.getPaidAt())).toList());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
