package com.retailerp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.security.CurrentUser;
import com.retailerp.tenancy.TenantContext;

/**
 * Writes journal entries. Every posting in the app goes through {@link #post}, which checks that debits equal
 * credits (the database checks it again at commit with a deferred constraint trigger).
 */
@Service
@Transactional
public class JournalService {

    private final JournalEntryRepository entries;
    private final DocumentNumberService numbers;
    private final JdbcTemplate jdbc;
    /** Tenants whose chart of accounts is known to exist (avoids re-checking on every posting). */
    private final Set<UUID> seeded = ConcurrentHashMap.newKeySet();

    public JournalService(JournalEntryRepository entries, DocumentNumberService numbers, JdbcTemplate jdbc) {
        this.entries = entries;
        this.numbers = numbers;
        this.jdbc = jdbc;
    }

    /** One side of an entry. Positive amounts only; use {@link #debit} / {@link #credit} to build them. */
    public record Posting(String account, BigDecimal debit, BigDecimal credit, String memo) {
    }

    public static Posting debit(String account, BigDecimal amount) {
        return amount.signum() >= 0 ? new Posting(account, amount, BigDecimal.ZERO, null) : new Posting(account, BigDecimal.ZERO, amount.negate(), null);
    }

    public static Posting credit(String account, BigDecimal amount) {
        return amount.signum() >= 0 ? new Posting(account, BigDecimal.ZERO, amount, null) : new Posting(account, amount.negate(), BigDecimal.ZERO, null);
    }

    /** Posts an entry; zero lines are dropped. Returns null when nothing is left (e.g. a free item with no cost). */
    public JournalEntry post(LocalDate date, String description, String sourceType, UUID sourceId, List<Posting> postings) {
        ensureChart();
        List<Posting> lines = new ArrayList<>(postings.stream().filter(p -> p.debit().signum() != 0 || p.credit().signum() != 0).toList());
        if (lines.isEmpty()) {
            return null;
        }
        BigDecimal debits = lines.stream().map(Posting::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().map(Posting::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new BusinessRuleException("Entry not balanced: debits " + debits + " ≠ credits " + credits + ".");
        }
        JournalEntry entry = new JournalEntry(numbers.next(DocumentType.JOURNAL_ENTRY), date, description, sourceType, sourceId, CurrentUser.id());
        for (Posting p : lines) {
            entry.add(new JournalLine(p.account(), p.debit(), p.credit(), p.memo()));
        }
        return entries.save(entry);
    }

    /** Creates the SCF accounts for the current tenant the first time it posts or reads accounting data. */
    public void ensureChart() {
        UUID tenant = TenantContext.get();
        if (tenant == null || seeded.contains(tenant)) {
            return;
        }
        for (ChartOfAccounts.Seed s : ChartOfAccounts.SEED) {
            jdbc.update("""
                    INSERT INTO accounts (tenant_id, code, name, type, system) VALUES (current_tenant_id(), ?, ?, ?, true)
                    ON CONFLICT (tenant_id, code) DO NOTHING
                    """, s.code(), s.name(), s.type().name());
        }
        // Remember it only once the transaction has committed (a rollback would also undo the inserts).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    seeded.add(tenant);
                }
            });
        }
    }
}
