package com.retailerp.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.Money;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.invoicing.InvoicingDtos.InvoiceResponse;
import com.retailerp.invoicing.InvoicingDtos.InvoiceSummary;
import com.retailerp.invoicing.InvoicingDtos.PaymentRequest;
import com.retailerp.invoicing.InvoicingDtos.PaymentResponse;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.sales.Customer;
import com.retailerp.sales.SalesDtos.LineResponse;
import com.retailerp.sales.SalesOrder;
import com.retailerp.security.CurrentUser;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final DocumentNumberService numbers;
    private final int paymentTermsDays;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, DocumentNumberService numbers,
            @Value("${app.invoice.payment-terms-days:30}") int paymentTermsDays) {
        this.invoices = invoices;
        this.payments = payments;
        this.numbers = numbers;
        this.paymentTermsDays = paymentTermsDays;
    }

    /** Called by SalesOrderService#confirm inside its transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice issueFor(SalesOrder order) {
        LocalDate today = LocalDate.now();
        return invoices.save(new Invoice(numbers.next(DocumentType.INVOICE), order, today, today.plusDays(paymentTermsDays)));
    }

    /** Called when a confirmed sales order is cancelled. Fails if the invoice already has payments. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void voidFor(SalesOrder order) {
        invoices.findBySalesOrderId(order.getId()).ifPresent(Invoice::voidInvoice);
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findFor(UUID salesOrderId) {
        return invoices.findBySalesOrderId(salesOrderId);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> search(InvoiceStatus status, String q, Pageable pageable) {
        return PageResponse.of(invoices.search(status, q == null ? "" : q.trim(), pageable), InvoiceSummary::from);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return toResponse(invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice", id)));
    }

    public InvoiceResponse recordPayment(UUID invoiceId, PaymentRequest request) {
        Invoice invoice = invoices.findForUpdate(invoiceId).orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
        BigDecimal amount = Money.round(request.amount());
        invoice.registerPayment(amount);
        payments.save(new Payment(invoice, amount, request.method(), request.reference(),
                request.paidAt() != null ? request.paidAt() : Instant.now(), CurrentUser.id()));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstandingTotal() {
        return invoices.outstandingTotal();
    }

    private InvoiceResponse toResponse(Invoice i) {
        Customer c = i.getCustomer();
        List<LineResponse> lines = i.getSalesOrder().getLines().stream().map(LineResponse::from).toList();
        List<PaymentResponse> paid = payments.findByInvoiceIdOrderByPaidAtAsc(i.getId()).stream().map(PaymentResponse::from).toList();
        return new InvoiceResponse(i.getId(), i.getNumber(), i.getSalesOrder().getId(), i.getSalesOrder().getNumber(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), c == null ? null : c.getEmail(),
                c == null ? null : c.getAddress(), i.getStatus(), i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getDiscountTotal(),
                i.getTaxTotal(),
                i.getTotal(), i.getAmountPaid(), i.balanceDue(), lines, paid);
    }
}
