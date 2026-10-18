package com.retailerp.accounting;

import static com.retailerp.accounting.ChartOfAccounts.BANK;
import static com.retailerp.accounting.ChartOfAccounts.CASH;
import static com.retailerp.accounting.ChartOfAccounts.COST_OF_GOODS_SOLD;
import static com.retailerp.accounting.ChartOfAccounts.CUSTOMERS;
import static com.retailerp.accounting.ChartOfAccounts.CUSTOMER_ADVANCES;
import static com.retailerp.accounting.ChartOfAccounts.OTHER_EXPENSES;
import static com.retailerp.accounting.ChartOfAccounts.OTHER_INCOME;
import static com.retailerp.accounting.ChartOfAccounts.SALARIES;
import static com.retailerp.accounting.ChartOfAccounts.SALES;
import static com.retailerp.accounting.ChartOfAccounts.SOCIAL_BODIES;
import static com.retailerp.accounting.ChartOfAccounts.SOCIAL_CHARGES;
import static com.retailerp.accounting.ChartOfAccounts.STAFF_PAYABLE;
import static com.retailerp.accounting.ChartOfAccounts.STOCK;
import static com.retailerp.accounting.ChartOfAccounts.SUPPLIERS;
import static com.retailerp.accounting.ChartOfAccounts.SUPPLIERS_UNBILLED;
import static com.retailerp.accounting.ChartOfAccounts.TAX_WITHHELD;
import static com.retailerp.accounting.ChartOfAccounts.VAT_COLLECTED;
import static com.retailerp.accounting.ChartOfAccounts.VAT_DEDUCTIBLE;
import static com.retailerp.accounting.JournalService.credit;
import static com.retailerp.accounting.JournalService.debit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.hr.PayrollApproved;
import com.retailerp.hr.PayrollPaid;
import com.retailerp.hr.PayrollRun;
import com.retailerp.hr.PayrollRunRepository;
import com.retailerp.hr.SalaryPaymentMethod;
import com.retailerp.inventory.StockAdjusted;
import com.retailerp.inventory.StockMovement;
import com.retailerp.inventory.StockMovementRepository;
import com.retailerp.invoicing.InvoicePaymentRecorded;
import com.retailerp.invoicing.Payment;
import com.retailerp.invoicing.PaymentMethod;
import com.retailerp.invoicing.PaymentRepository;
import com.retailerp.payables.SupplierBill;
import com.retailerp.payables.SupplierBillApproved;
import com.retailerp.payables.SupplierBillRepository;
import com.retailerp.payables.SupplierBillVoided;
import com.retailerp.payables.SupplierPayment;
import com.retailerp.payables.SupplierPaymentMethod;
import com.retailerp.payables.SupplierPaymentRecorded;
import com.retailerp.payables.SupplierPaymentRepository;
import com.retailerp.purchasing.PurchaseOrder;
import com.retailerp.purchasing.PurchaseOrderReceived;
import com.retailerp.purchasing.PurchaseOrderRepository;
import com.retailerp.returns.SalesReturn;
import com.retailerp.returns.SalesReturnCreated;
import com.retailerp.returns.SalesReturnLine;
import com.retailerp.returns.SalesReturnRepository;
import com.retailerp.sales.SalesOrder;
import com.retailerp.sales.SalesOrderCancelled;
import com.retailerp.sales.SalesOrderConfirmed;
import com.retailerp.sales.SalesOrderLine;
import com.retailerp.sales.SalesOrderRepository;

/**
 * Turns business events into journal entries (automatic bookkeeping). The other modules don't know this class
 * exists: they publish events; this class listens. Each listener runs inside the business transaction, so a
 * document and its accounting entry are saved together or not at all.
 * <p>
 * Stock is kept "perpetually": goods received go INTO account 300 (Stocks), goods sold go OUT of it into
 * 600 (cost of goods sold), so 300 always matches the value of the stock movements.
 */
@Component
public class AccountingPostings {

    private final JournalService journal;
    private final SalesOrderRepository salesOrders;
    private final PaymentRepository payments;
    private final SalesReturnRepository returns;
    private final PurchaseOrderRepository purchaseOrders;
    private final SupplierBillRepository bills;
    private final SupplierPaymentRepository supplierPayments;
    private final StockMovementRepository movements;
    private final PayrollRunRepository payrollRuns;
    private final JdbcTemplate jdbc;

    public AccountingPostings(JournalService journal, SalesOrderRepository salesOrders, PaymentRepository payments,
            SalesReturnRepository returns, PurchaseOrderRepository purchaseOrders, SupplierBillRepository bills,
            SupplierPaymentRepository supplierPayments, StockMovementRepository movements, PayrollRunRepository payrollRuns, JdbcTemplate jdbc) {
        this.journal = journal;
        this.salesOrders = salesOrders;
        this.payments = payments;
        this.returns = returns;
        this.purchaseOrders = purchaseOrders;
        this.bills = bills;
        this.supplierPayments = supplierPayments;
        this.movements = movements;
        this.payrollRuns = payrollRuns;
        this.jdbc = jdbc;
    }

    /** Sale: Dr 411 Clients (TTC) / Cr 700 Ventes (HT, after discounts) / Cr 4457 TVA collectée. Plus cost: Dr 600 / Cr 300. */
    @EventListener
    public void onSaleConfirmed(SalesOrderConfirmed event) {
        SalesOrder so = salesOrders.findWithLines(event.salesOrderId()).orElseThrow();
        BigDecimal net = so.getSubtotal().subtract(so.getDiscountTotal());
        journal.post(LocalDate.now(), "Sale " + so.getNumber(), "SALE", so.getId(), List.of(
                debit(CUSTOMERS, so.getTotal()),
                credit(SALES, net),
                credit(VAT_COLLECTED, so.getTaxTotal())));
        BigDecimal cost = costOf(so.getLines());
        journal.post(LocalDate.now(), "Cost of goods sold " + so.getNumber(), "SALE_COST", so.getId(), List.of(
                debit(COST_OF_GOODS_SOLD, cost),
                credit(STOCK, cost)));
    }

    /** Cancelling a confirmed sale reverses both entries above (a reversing entry, never a deletion). */
    @EventListener
    public void onSaleCancelled(SalesOrderCancelled event) {
        SalesOrder so = salesOrders.findWithLines(event.salesOrderId()).orElseThrow();
        BigDecimal net = so.getSubtotal().subtract(so.getDiscountTotal());
        journal.post(LocalDate.now(), "Cancelled sale " + so.getNumber(), "SALE_CANCEL", so.getId(), List.of(
                debit(SALES, net),
                debit(VAT_COLLECTED, so.getTaxTotal()),
                credit(CUSTOMERS, so.getTotal())));
        BigDecimal cost = costOf(so.getLines());
        journal.post(LocalDate.now(), "Stock back from cancelled sale " + so.getNumber(), "SALE_CANCEL_COST", so.getId(), List.of(
                debit(STOCK, cost),
                credit(COST_OF_GOODS_SOLD, cost)));
    }

    /** Customer payment: Dr 530 Caisse / 512 Banque / 4191 (store credit used) — Cr 411 Clients. */
    @EventListener
    public void onCustomerPayment(InvoicePaymentRecorded event) {
        Payment p = payments.findById(event.paymentId()).orElseThrow();
        String account = p.getMethod() == PaymentMethod.CASH ? CASH : p.getMethod() == PaymentMethod.STORE_CREDIT ? CUSTOMER_ADVANCES : BANK;
        journal.post(LocalDate.now(), "Payment of " + p.getInvoice().getNumber(), "CUSTOMER_PAYMENT", p.getId(), List.of(
                debit(account, p.getAmount()),
                credit(CUSTOMERS, p.getAmount())));
    }

    /**
     * Return (credit note): reverse the sale part (Dr 700, Dr 4457 / Cr 411); restocked items go back into stock at their
     * cost (Dr 300 / Cr 600); written-off items stay a cost. A refund then clears the customer: Dr 411 / Cr 530, 512 or 4191.
     */
    @EventListener
    public void onReturn(SalesReturnCreated event) {
        SalesReturn r = returns.findWithLines(event.salesReturnId()).orElseThrow();
        BigDecimal net = r.getSubtotal().subtract(r.getDiscountTotal());
        journal.post(LocalDate.now(), "Credit note " + r.getNumber(), "RETURN", r.getId(), List.of(
                debit(SALES, net),
                debit(VAT_COLLECTED, r.getTaxTotal()),
                credit(CUSTOMERS, r.getTotal())));
        BigDecimal restocked = BigDecimal.ZERO;
        for (SalesReturnLine l : r.getLines()) {
            if (l.isRestock()) {
                restocked = restocked.add(l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity())));
            }
        }
        journal.post(LocalDate.now(), "Restocked from " + r.getNumber(), "RETURN_COST", r.getId(), List.of(
                debit(STOCK, restocked),
                credit(COST_OF_GOODS_SOLD, restocked)));
        if (r.getRefundAmount().signum() > 0) {
            String account = r.getRefundMethod() == PaymentMethod.CASH ? CASH
                    : r.getRefundMethod() == PaymentMethod.STORE_CREDIT ? CUSTOMER_ADVANCES : BANK;
            journal.post(LocalDate.now(), "Refund for " + r.getNumber(), "REFUND", r.getId(), List.of(
                    debit(CUSTOMERS, r.getRefundAmount()),
                    credit(account, r.getRefundAmount())));
        }
    }

    /** Goods received: Dr 300 Stocks / Cr 408 Fournisseurs – factures non parvenues (we owe it, the bill hasn't come yet). */
    @EventListener
    public void onGoodsReceived(PurchaseOrderReceived event) {
        PurchaseOrder po = purchaseOrders.findWithLines(event.purchaseOrderId()).orElseThrow();
        journal.post(LocalDate.now(), "Goods received " + po.getNumber(), "GOODS_RECEIPT", po.getId(), List.of(
                debit(STOCK, po.getTotal()),
                credit(SUPPLIERS_UNBILLED, po.getTotal())));
    }

    /**
     * Supplier bill approved. For goods: Dr 408 (clears the receipt), any difference (delivery fee, price change) is part
     * of the stock's cost (Dr/Cr 300), Dr 4456 TVA déductible, Cr 401 Fournisseurs. For other bills (rent...): Dr the
     * expense account instead of 408/300.
     */
    @EventListener
    public void onBillApproved(SupplierBillApproved event) {
        SupplierBill b = bills.findWithLines(event.billId()).orElseThrow();
        journal.post(b.getBillDate(), "Bill " + b.getNumber() + " · " + b.getSupplier().getName(), "SUPPLIER_BILL", b.getId(), billPostings(b));
    }

    @EventListener
    public void onBillVoided(SupplierBillVoided event) {
        if (!event.wasApproved()) {
            return; // a draft was never in the books
        }
        SupplierBill b = bills.findWithLines(event.billId()).orElseThrow();
        List<JournalService.Posting> reversed = billPostings(b).stream()
                .map(p -> new JournalService.Posting(p.account(), p.credit(), p.debit(), p.memo())).toList();
        journal.post(LocalDate.now(), "Voided bill " + b.getNumber(), "SUPPLIER_BILL_VOID", b.getId(), reversed);
    }

    private List<JournalService.Posting> billPostings(SupplierBill b) {
        if (b.getPurchaseOrder() != null) {
            BigDecimal received = b.getPurchaseOrder().getTotal();
            return List.of(
                    debit(SUPPLIERS_UNBILLED, received),
                    debit(STOCK, b.getSubtotal().subtract(received)),
                    debit(VAT_DEDUCTIBLE, b.getTaxTotal()),
                    credit(SUPPLIERS, b.getTotal()));
        }
        String expense = b.getExpenseAccount() == null ? ChartOfAccounts.DEFAULT_EXPENSE : b.getExpenseAccount();
        requireAccount(expense);
        return List.of(
                debit(expense, b.getSubtotal()),
                debit(VAT_DEDUCTIBLE, b.getTaxTotal()),
                credit(SUPPLIERS, b.getTotal()));
    }

    /** Paying a supplier: Dr 401 Fournisseurs / Cr 530 Caisse (cash) or 512 Banque. */
    @EventListener
    public void onSupplierPayment(SupplierPaymentRecorded event) {
        SupplierPayment p = supplierPayments.findById(event.paymentId()).orElseThrow();
        String account = p.getMethod() == SupplierPaymentMethod.CASH ? CASH : BANK;
        journal.post(LocalDate.now(), "Payment of " + p.getBill().getNumber(), "SUPPLIER_PAYMENT", p.getId(), List.of(
                debit(SUPPLIERS, p.getAmount()),
                credit(account, p.getAmount())));
    }

    /** Stock found: Dr 300 / Cr 758 (gain). Stock lost or damaged: Dr 658 (loss) / Cr 300. Valued at the current cost price. */
    @EventListener
    public void onStockAdjusted(StockAdjusted event) {
        StockMovement m = movements.findById(event.movementId()).orElseThrow();
        BigDecimal value = m.getProduct().getCostPrice().multiply(BigDecimal.valueOf(Math.abs(m.getQuantity())));
        String what = "Stock adjustment " + m.getProduct().getSku() + " (" + (m.getQuantity() > 0 ? "+" : "") + m.getQuantity() + ")";
        if (m.getQuantity() > 0) {
            journal.post(LocalDate.now(), what, "STOCK_ADJUSTMENT", m.getId(), List.of(debit(STOCK, value), credit(OTHER_INCOME, value)));
        } else {
            journal.post(LocalDate.now(), what, "STOCK_ADJUSTMENT", m.getId(), List.of(debit(OTHER_EXPENSES, value), credit(STOCK, value)));
        }
    }

    /**
     * Payroll approved (salaries owed): Dr 631 salaries (gross) and Dr 635 employer CNAS / Cr 431 CNAS (employee +
     * employer shares), Cr 442 IRG withheld for the State, Cr 421 net salaries owed to the staff.
     */
    @EventListener
    public void onPayrollApproved(PayrollApproved event) {
        PayrollRun r = payrollRuns.findById(event.runId()).orElseThrow();
        journal.post(LocalDate.now(), "Payroll " + r.getPeriod() + " (" + r.getNumber() + ")", "PAYROLL", r.getId(), List.of(
                debit(SALARIES, r.getTotalGross()),
                debit(SOCIAL_CHARGES, r.getTotalCnasEmployer()),
                credit(SOCIAL_BODIES, r.getTotalCnasEmployee().add(r.getTotalCnasEmployer())),
                credit(TAX_WITHHELD, r.getTotalIrg()),
                credit(STAFF_PAYABLE, r.getTotalNet())));
    }

    /** Net salaries paid: Dr 421 / Cr 530 Caisse or 512 Banque. CNAS and IRG are paid to their bodies later (manual entries). */
    @EventListener
    public void onPayrollPaid(PayrollPaid event) {
        PayrollRun r = payrollRuns.findById(event.runId()).orElseThrow();
        String account = r.getPaymentMethod() == SalaryPaymentMethod.CASH ? CASH : BANK;
        journal.post(LocalDate.now(), "Salaries paid " + r.getPeriod() + " (" + r.getNumber() + ")", "PAYROLL_PAYMENT", r.getId(), List.of(
                debit(STAFF_PAYABLE, r.getTotalNet()),
                credit(account, r.getTotalNet())));
    }

    private static BigDecimal costOf(List<SalesOrderLine> lines) {
        return lines.stream().map(l -> l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireAccount(String code) {
        journal.ensureChart();
        Integer found = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE code = ?", Integer.class, code);
        if (found == null || found == 0) {
            throw new BusinessRuleException("Unknown expense account " + code + ". Add it to the chart of accounts first.");
        }
    }
}
