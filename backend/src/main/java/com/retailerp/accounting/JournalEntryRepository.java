package com.retailerp.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    @EntityGraph(attributePaths = "lines")
    @Query("""
            select e from JournalEntry e
            where e.entryDate between :from and :to
              and (:source = '' or e.sourceType = :source)
            """)
    Page<JournalEntry> search(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("source") String source, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    @Query("select e from JournalEntry e where e.id = :id")
    Optional<JournalEntry> findWithLines(@Param("id") UUID id);

    @EntityGraph(attributePaths = "lines")
    List<JournalEntry> findBySourceTypeAndSourceIdOrderByCreatedAtAsc(String sourceType, UUID sourceId);
}
