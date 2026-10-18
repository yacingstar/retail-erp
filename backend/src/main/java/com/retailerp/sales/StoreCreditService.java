package com.retailerp.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.sales.StoreCreditEntry.Reason;
import com.retailerp.security.CurrentUser;

/**
 * Store credit = a ledger per customer (no mutable "balance" column). Spending locks the customer with an advisory
 * lock first, so two payments can't both spend the same credit (same idea as stock).
 */
@Service
@Transactional
public class StoreCreditService {

    private final StoreCreditRepository entries;
    private final CustomerRepository customers;
    private final JdbcTemplate jdbc;

    public StoreCreditService(StoreCreditRepository entries, CustomerRepository customers, JdbcTemplate jdbc) {
        this.entries = entries;
        this.customers = customers;
        this.jdbc = jdbc;
    }

    public record EntryResponse(BigDecimal amount, Reason reason, UUID referenceId, String note, Instant createdAt) {
    }

    public record CreditResponse(UUID customerId, BigDecimal balance, List<EntryResponse> entries) {
    }

    @Transactional(readOnly = true)
    public CreditResponse statement(UUID customerId) {
        customer(customerId);
        return new CreditResponse(customerId, entries.balanceOf(customerId), entries.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(e -> new EntryResponse(e.getAmount(), e.getReason(), e.getReferenceId(), e.getNote(), e.getCreatedAt())).toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(UUID customerId) {
        return entries.balanceOf(customerId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void give(Customer customer, BigDecimal amount, UUID referenceId, String note) {
        entries.save(new StoreCreditEntry(customer, amount, Reason.RETURN_REFUND, referenceId, note, CurrentUser.id()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void spend(Customer customer, BigDecimal amount, UUID referenceId, String note) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, "credit:" + customer.getId());
        BigDecimal balance = entries.balanceOf(customer.getId());
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException(customer.getName() + " has only " + balance + " store credit (needed " + amount + ").");
        }
        entries.save(new StoreCreditEntry(customer, amount.negate(), Reason.PAYMENT, referenceId, note, CurrentUser.id()));
    }

    private Customer customer(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }
}
