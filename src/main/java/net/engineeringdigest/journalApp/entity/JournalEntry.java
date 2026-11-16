package net.engineeringdigest.journalApp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A journal entry. The server only ever holds the ciphertext; title, content and mood are
 * encrypted in the browser with the owner's data key, using the entry id as associated data
 * (so ciphertexts cannot be moved between entries without detection).
 */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String ciphertext;

    @Column(nullable = false, length = 24)
    private String iv;

    @Column(name = "enc_version", nullable = false)
    private short encVersion = 1;

    @Column(name = "report_sentiment_enc", columnDefinition = "text")
    private String reportSentimentEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public JournalEntry(UUID id, UUID userId) {
        this.id = id;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
