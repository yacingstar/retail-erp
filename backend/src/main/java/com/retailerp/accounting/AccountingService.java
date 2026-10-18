package com.retailerp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.accounting.AccountingDtos.AccountBalance;
import com.retailerp.accounting.AccountingDtos.AccountRequest;
import com.retailerp.accounting.AccountingDtos.AccountResponse;
import com.retailerp.accounting.AccountingDtos.BalanceSheet;
import com.retailerp.accounting.AccountingDtos.EntryResponse;
import com.retailerp.accounting.AccountingDtos.Ledger;
import com.retailerp.accounting.AccountingDtos.LedgerRow;
import com.retailerp.accounting.AccountingDtos.LineResponse;
import com.retailerp.accounting.AccountingDtos.ManualEntryRequest;
import com.retailerp.accounting.AccountingDtos.ProfitAndLoss;
import com.retailerp.accounting.AccountingDtos.TrialBalance;
import com.retailerp.accounting.AccountingDtos.VatReport;
import com.retailerp.accounting.AccountingDtos.VatRow;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;

/**
 * Reading the books (journal, ledger, statements) and manual entries. Statements are computed from the journal lines
 * with plain SQL: nothing is stored twice, so they can never disagree with the journal.
 */
@Service
@Transactional
public class AccountingService {

    private final JournalService journal;
    private final JournalEntryRepository entries;
    private final JdbcTemplate jdbc;

    public AccountingService(JournalService journal, JournalEntryRepository entries, JdbcTemplate jdbc) {
        this.journal = journal;
        this.entries = entries;
        this.jdbc = jdbc;
    }

    // --- chart of accounts -----------------------------------------------------------------------------------------------

    public List<AccountResponse> accounts() {
        journal.ensureChart();
        return jdbc.query("SELECT code, name, type, system FROM accounts ORDER BY code", (rs, i) -> new AccountResponse(rs.getString("code"),
                rs.getString("name"), AccountType.valueOf(rs.getString("type")), rs.getBoolean("system")));
    }

    public AccountResponse addAccount(AccountRequest request) {
        journal.ensureChart();
        int inserted = jdbc.update("""
                INSERT INTO accounts (tenant_id, code, name, type) VALUES (current_tenant_id(), ?, ?, ?)
                ON CONFLICT (tenant_id, code) DO NOTHING
                """, request.code(), request.name().trim(), request.type().name());
        if (inserted == 0) {
            throw new BusinessRuleException("Account " + request.code() + " already exists.");
        }
        return new AccountResponse(request.code(), request.name().trim(), request.type(), false);
    }

    // --- journal -----------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> journal(LocalDate from, LocalDate to, String source, Pageable pageable) {
        Map<String, String> names = accountNames();
        return PageResponse.of(entries.search(from, to, source == null ? "" : source, pageable), e -> toResponse(e, names));
    }

    @Transactional(readOnly = true)
    public EntryResponse entry(UUID id) {
        return toResponse(entries.findWithLines(id).orElseThrow(() -> new NotFoundException("Journal entry", id)), accountNames());
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> forSource(UUID sourceId) {
        Map<String, String> names = accountNames();
        return jdbc.queryForList("SELECT id FROM journal_entries WHERE source_id = ? ORDER BY created_at", UUID.class, sourceId).stream()
                .map(id -> toResponse(entries.findWithLines(id).orElseThrow(), names)).toList();
    }

    /** Admin-only manual entry (a loan, the owner's capital...). Must balance, and use existing accounts. */
    public EntryResponse manualEntry(ManualEntryRequest request) {
        journal.ensureChart();
        Map<String, String> names = accountNames();
        List<JournalService.Posting> postings = new ArrayList<>();
        for (var line : request.lines()) {
            if (!names.containsKey(line.accountCode())) {
                throw new IllegalArgumentException("Unknown account " + line.accountCode() + ".");
            }
            if ((line.debit().signum() > 0) == (line.credit().signum() > 0)) {
                throw new IllegalArgumentException("Each line needs either a debit or a credit (not both, not neither).");
            }
            postings.add(new JournalService.Posting(line.accountCode(), line.debit(), line.credit(), line.memo()));
        }
        JournalEntry entry = journal.post(request.entryDate() != null ? request.entryDate() : LocalDate.now(), request.description().trim(),
                "MANUAL", null, postings);
        return toResponse(entry, names);
    }

    // --- statements --------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate from, LocalDate to) {
        List<AccountBalance> rows = balances("e.entry_date BETWEEN ? AND ?", from, to).stream()
                .filter(b -> b.debit().signum() != 0 || b.credit().signum() != 0).toList();
        return new TrialBalance(from, to, rows, sum(rows, AccountBalance::debit), sum(rows, AccountBalance::credit));
    }

    @Transactional(readOnly = true)
    public Ledger ledger(String code, LocalDate from, LocalDate to) {
        AccountResponse account = accounts().stream().filter(a -> a.code().equals(code)).findFirst()
                .orElseThrow(() -> new NotFoundException("Account", code));
        int sign = account.type().debitNormal() ? 1 : -1;
        BigDecimal opening = jdbc.queryForObject("""
                SELECT coalesce(sum(l.debit - l.credit), 0) FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
                WHERE l.account_code = ? AND e.entry_date < ?
                """, BigDecimal.class, code, from).multiply(BigDecimal.valueOf(sign));
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal running = opening;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT e.entry_date, e.id, e.number, e.description, l.debit, l.credit
                FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
                WHERE l.account_code = ? AND e.entry_date BETWEEN ? AND ?
                ORDER BY e.entry_date, e.created_at, l.line_no
                """, code, from, to)) {
            BigDecimal debit = (BigDecimal) r.get("debit");
            BigDecimal credit = (BigDecimal) r.get("credit");
            running = running.add(debit.subtract(credit).multiply(BigDecimal.valueOf(sign)));
            rows.add(new LedgerRow(((java.sql.Date) r.get("entry_date")).toLocalDate(), (UUID) r.get("id"), (String) r.get("number"),
                    (String) r.get("description"), debit, credit, running));
        }
        return new Ledger(code, account.name(), account.type(), from, to, opening, rows, running);
    }

    @Transactional(readOnly = true)
    public ProfitAndLoss profitAndLoss(LocalDate from, LocalDate to) {
        List<AccountBalance> all = balances("e.entry_date BETWEEN ? AND ?", from, to);
        List<AccountBalance> income = nonZero(all, AccountType.INCOME);
        List<AccountBalance> expenses = nonZero(all, AccountType.EXPENSE);
        BigDecimal totalIncome = sum(income, AccountBalance::balance);
        BigDecimal totalExpenses = sum(expenses, AccountBalance::balance);
        return new ProfitAndLoss(from, to, income, totalIncome, expenses, totalExpenses, totalIncome.subtract(totalExpenses));
    }

    /**
     * What the business owns (assets) and owes (liabilities), plus equity, at a date. The result not yet closed into
     * equity (income − expenses so far) is shown as "current result", so that assets = liabilities + equity.
     */
    @Transactional(readOnly = true)
    public BalanceSheet balanceSheet(LocalDate date) {
        List<AccountBalance> all = balances("e.entry_date <= ?", date);
        List<AccountBalance> assets = nonZero(all, AccountType.ASSET);
        List<AccountBalance> liabilities = nonZero(all, AccountType.LIABILITY);
        List<AccountBalance> equity = nonZero(all, AccountType.EQUITY);
        BigDecimal result = sum(nonZero(all, AccountType.INCOME), AccountBalance::balance)
                .subtract(sum(nonZero(all, AccountType.EXPENSE), AccountBalance::balance));
        BigDecimal totalAssets = sum(assets, AccountBalance::balance);
        BigDecimal totalLiabilities = sum(liabilities, AccountBalance::balance);
        BigDecimal totalEquity = sum(equity, AccountBalance::balance).add(result);
        return new BalanceSheet(date, assets, totalAssets, liabilities, totalLiabilities, equity, result, totalEquity,
                totalLiabilities.add(totalEquity));
    }

    /**
     * TVA for a period, from the documents: collected on sales (minus returns) and deductible on approved supplier
     * bills, grouped by rate (19 %, 9 %...). Payable = collected − deductible (negative = a credit to carry forward).
     */
    @Transactional(readOnly = true)
    public VatReport vat(LocalDate from, LocalDate to) {
        List<VatRow> sales = jdbc.query("""
                SELECT rate, sum(base) AS base, sum(tax) AS tax FROM (
                    SELECT l.tax_rate AS rate, l.line_subtotal - l.line_discount AS base, l.line_tax AS tax
                    FROM sales_order_lines l JOIN sales_orders o ON o.id = l.sales_order_id
                    WHERE o.status = 'CONFIRMED' AND o.order_date BETWEEN ? AND ?
                    UNION ALL
                    SELECT sl.tax_rate, -(rl.line_subtotal - rl.line_discount), -rl.line_tax
                    FROM sales_return_lines rl JOIN sales_returns r ON r.id = rl.sales_return_id
                    JOIN sales_order_lines sl ON sl.id = rl.sales_order_line_id
                    WHERE r.return_date BETWEEN ? AND ?
                ) t GROUP BY rate ORDER BY rate DESC
                """, (rs, i) -> new VatRow(rs.getBigDecimal("rate"), rs.getBigDecimal("base"), rs.getBigDecimal("tax")), from, to, from, to);
        List<VatRow> purchases = jdbc.query("""
                SELECT l.tax_rate AS rate, sum(l.line_total) AS base, sum(l.line_tax) AS tax
                FROM supplier_bill_lines l JOIN supplier_bills b ON b.id = l.bill_id
                WHERE b.status IN ('OPEN', 'PARTIALLY_PAID', 'PAID') AND b.bill_date BETWEEN ? AND ?
                GROUP BY l.tax_rate ORDER BY l.tax_rate DESC
                """, (rs, i) -> new VatRow(rs.getBigDecimal("rate"), rs.getBigDecimal("base"), rs.getBigDecimal("tax")), from, to);
        BigDecimal collected = sum(sales, VatRow::tax);
        BigDecimal deductible = sum(purchases, VatRow::tax);
        return new VatReport(from, to, sales, collected, purchases, deductible, collected.subtract(deductible));
    }

    // --- helpers -----------------------------------------------------------------------------------------------------------

    /** Debit and credit totals per account for the lines matching the condition; balance in the account's normal direction. */
    private List<AccountBalance> balances(String condition, Object... params) {
        journal.ensureChart();
        return jdbc.query("""
                SELECT a.code, a.name, a.type, coalesce(sum(x.debit), 0) AS debit, coalesce(sum(x.credit), 0) AS credit
                FROM accounts a
                LEFT JOIN (SELECT l.account_code, l.debit, l.credit FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
                           WHERE %s) x ON x.account_code = a.code
                GROUP BY a.code, a.name, a.type ORDER BY a.code
                """.formatted(condition), (rs, i) -> {
            AccountType type = AccountType.valueOf(rs.getString("type"));
            BigDecimal debit = rs.getBigDecimal("debit");
            BigDecimal credit = rs.getBigDecimal("credit");
            BigDecimal balance = type.debitNormal() ? debit.subtract(credit) : credit.subtract(debit);
            return new AccountBalance(rs.getString("code"), rs.getString("name"), type, debit, credit, balance);
        }, params);
    }

    private static List<AccountBalance> nonZero(List<AccountBalance> all, AccountType type) {
        return all.stream().filter(b -> b.type() == type && b.balance().signum() != 0).toList();
    }

    private Map<String, String> accountNames() {
        journal.ensureChart();
        return jdbc.query("SELECT code, name FROM accounts", (rs, i) -> Map.entry(rs.getString(1), rs.getString(2))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static EntryResponse toResponse(JournalEntry e, Map<String, String> names) {
        List<LineResponse> lines = e.getLines().stream().map(l -> new LineResponse(l.getLineNo(), l.getAccountCode(),
                names.getOrDefault(l.getAccountCode(), ""), l.getDebit(), l.getCredit(), l.getMemo())).toList();
        return new EntryResponse(e.getId(), e.getNumber(), e.getEntryDate(), e.getDescription(), e.getSourceType(), e.getSourceId(),
                sum(lines, LineResponse::debit), lines, e.getCreatedAt());
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
