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

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** Taken off by credit notes (returns). */
    @Column(name = "amount_credited", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountCredited = BigDecimal.ZERO;

    /** Money (or store credit) given back to the customer. */
    @Column(name = "amount_refunded", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    protected Invoice() {
    }

    public Invoice(String number, SalesOrder order, LocalDate issueDate, LocalDate dueDate) {
        this.number = number;
        this.salesOrder = order;
        this.customer = order.getCustomer();
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.subtotal = order.getSubtotal();
        this.discountTotal = order.getDiscountTotal();
        this.taxTotal = order.getTaxTotal();
        this.total = order.getTotal();
    }

    /** What the customer still owes (never negative). */
    public BigDecimal balanceDue() {
        return total.subtract(amountPaid).subtract(amountCredited).max(BigDecimal.ZERO);
    }

    /** What we owe the customer: they paid more than the invoice is worth after credit notes. */
    public BigDecimal refundDue() {
        return amountPaid.add(amountCredited).subtract(total).subtract(amountRefunded).max(BigDecimal.ZERO);
    }

    void registerPayment(BigDecimal amount) {
        if (status == InvoiceStatus.VOID || balanceDue().signum() == 0) {
            throw new BusinessRuleException("Invoice " + number + " has nothing left to pay.");
        }
        if (amount.compareTo(balanceDue()) > 0) {
            throw new BusinessRuleException("Payment " + amount + " exceeds the balance due " + balanceDue() + ".");
        }
        amountPaid = amountPaid.add(amount);
        updateStatus();
    }

    /** A credit note (return) reduces what the invoice is worth. */
    public void applyCredit(BigDecimal amount) {
        if (status == InvoiceStatus.VOID) {
            throw new BusinessRuleException("Invoice " + number + " is void.");
        }
        amountCredited = amountCredited.add(amount);
        updateStatus();
    }

    /** Money or store credit given back; can't exceed what's due. */
    public void recordRefund(BigDecimal amount) {
        if (amount.compareTo(refundDue()) > 0) {
            throw new BusinessRuleException("Refund " + amount + " exceeds the refund due " + refundDue() + ".");
        }
        amountRefunded = amountRefunded.add(amount);
    }

    private void updateStatus() {
        if (balanceDue().signum() > 0) {
            status = amountPaid.signum() > 0 || amountCredited.signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.ISSUED;
        } else {
            status = amountPaid.signum() > 0 ? InvoiceStatus.PAID : InvoiceStatus.CREDITED;
        }
    }

    public void voidInvoice() {
        if (amountPaid.signum() > 0) {
            throw new BusinessRuleException("Invoice " + number + " already has payments; it can't be voided.");
        }
        if (amountCredited.signum() > 0) {
            throw new BusinessRuleException("Invoice " + number + " has credit notes (returns); it can't be voided.");
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

    public BigDecimal getDiscountTotal() {
        return discountTotal;
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

    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    public BigDecimal getAmountRefunded() {
        return amountRefunded;
    }
}
