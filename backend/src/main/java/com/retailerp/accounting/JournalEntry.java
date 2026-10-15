package com.retailerp.accounting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.retailerp.common.TenantOwnedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** One accounting event (a sale, a payment...): several lines whose debits equal their credits. Never edited. */
@Entity
@Immutable
@Table(name = "journal_entries")
public class JournalEntry extends TenantOwnedEntity {

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST)
    @OrderBy("lineNo")
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    JournalEntry(String number, LocalDate entryDate, String description, String sourceType, UUID sourceId, UUID createdBy) {
        this.number = number;
        this.entryDate = entryDate;
        this.description = description;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdBy = createdBy;
    }

    void add(JournalLine line) {
        line.attach(this, lines.size() + 1);
        lines.add(line);
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public List<JournalLine> getLines() {
        return lines;
    }
}
