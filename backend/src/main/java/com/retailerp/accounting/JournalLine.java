package com.retailerp.accounting;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.retailerp.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "journal_lines")
public class JournalLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", nullable = false, length = 10)
    private String accountCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal credit;

    @Column(length = 200)
    private String memo;

    protected JournalLine() {
    }

    JournalLine(String accountCode, BigDecimal debit, BigDecimal credit, String memo) {
        this.accountCode = accountCode;
        this.debit = debit;
        this.credit = credit;
        this.memo = memo;
    }

    void attach(JournalEntry entry, int lineNo) {
        this.entry = entry;
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getMemo() {
        return memo;
    }
}
