package com.retailerp.invoicing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.retailerp.common.AuditedEntity;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.sales.Customer;
import com.retailerp.sales.SalesOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Issued automatically when a sales order is confirmed. Amounts are copied from the order (an invoice must never change). */
@Entity
@Table(name = "invoices")
public class Invoice extends AuditedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false, updatable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    protected Invoice() {
    }

    public Invoice(String number, SalesOrder order, LocalDate issueDate, LocalDate dueDate) {
        this.number = number;
        this.salesOrder = order;
        this.customer = order.getCustomer();
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.subtotal = order.getSubtotal();
        this.taxTotal = order.getTaxTotal();
        this.total = order.getTotal();
    }

    public BigDecimal balanceDue() {
        return total.subtract(amountPaid);
    }

    void registerPayment(BigDecimal amount) {
        if (status == InvoiceStatus.VOID || status == InvoiceStatus.PAID) {
            throw new BusinessRuleException("Invoice " + number + " is " + status + "; no payment can be recorded.");
        }
        if (amount.compareTo(balanceDue()) > 0) {
            throw new BusinessRuleException("Payment " + amount + " exceeds the balance due " + balanceDue() + ".");
        }
        amountPaid = amountPaid.add(amount);
        status = balanceDue().signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    public void voidInvoice() {
        if (amountPaid.signum() > 0) {
            throw new BusinessRuleException("Invoice " + number + " already has payments; it can't be voided.");
        }
        status = InvoiceStatus.VOID;
    }

    public String getNumber() {
        return number;
    }

    public SalesOrder getSalesOrder() {
        return salesOrder;
    }

    public Customer getCustomer() {
        return customer;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }
}
