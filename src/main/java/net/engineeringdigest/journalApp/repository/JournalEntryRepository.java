package net.engineeringdigest.journalApp.repository;

import net.engineeringdigest.journalApp.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every lookup is scoped by owner. There is deliberately no unscoped "find by id" used by the
 * web layer: an entry that is not yours behaves exactly like one that does not exist.
 */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Page<JournalEntry> findByUserId(UUID userId, Pageable pageable);

    Optional<JournalEntry> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("delete from JournalEntry e where e.id = :id and e.userId = :userId")
    int deleteOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("update JournalEntry e set e.reportSentimentEnc = null where e.userId = :userId and e.reportSentimentEnc is not null")
    int clearReportSentiments(@Param("userId") UUID userId);

    @Query("select e.reportSentimentEnc from JournalEntry e "
            + "where e.userId = :userId and e.createdAt >= :since and e.reportSentimentEnc is not null")
    List<String> findReportSentimentsSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
