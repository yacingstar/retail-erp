package com.retailerp.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-readable, per-tenant document numbers: PO-000001, SO-000001, INV-000001.
 * <p>
 * The upsert takes a row lock on the tenant's counter until the transaction ends, so two concurrent orders never
 * get the same number, and a rolled-back transaction gives its number back (no gaps from failed requests).
 */
@Service
public class DocumentNumberService {

    private final JdbcTemplate jdbc;

    public DocumentNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum DocumentType {
        PURCHASE_ORDER("PO"), SALES_ORDER("SO"), INVOICE("INV"), CREDIT_NOTE("CN"), SUPPLIER_BILL("BILL");

        private final String prefix;

        DocumentType(String prefix) {
            this.prefix = prefix;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentType type) {
        Long value = jdbc.queryForObject("""
                INSERT INTO document_sequences (tenant_id, doc_type, next_value) VALUES (current_tenant_id(), ?, 2)
                ON CONFLICT (tenant_id, doc_type) DO UPDATE SET next_value = document_sequences.next_value + 1
                RETURNING next_value - 1
                """, Long.class, type.name());
        return "%s-%06d".formatted(type.prefix, value);
    }
}
