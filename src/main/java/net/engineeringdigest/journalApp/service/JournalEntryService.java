package net.engineeringdigest.journalApp.service;

import net.engineeringdigest.journalApp.crypto.DataCipher;
import net.engineeringdigest.journalApp.dto.EntryDtos.*;
import net.engineeringdigest.journalApp.dto.PageResponse;
import net.engineeringdigest.journalApp.entity.JournalEntry;
import net.engineeringdigest.journalApp.entity.Sentiment;
import net.engineeringdigest.journalApp.entity.User;
import net.engineeringdigest.journalApp.exception.ApiException;
import net.engineeringdigest.journalApp.repository.JournalEntryRepository;
import net.engineeringdigest.journalApp.repository.UserRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Journal entries are opaque ciphertext to this service. Every operation is scoped to the caller's
 * user id; a foreign or missing id yields the same 404.
 */
@Service
public class JournalEntryService {
    public static final String SENTIMENT_FIELD = "journal_entries.report_sentiment";

    private final JournalEntryRepository entries;
    private final UserRepository users;
    private final DataCipher cipher;

    public JournalEntryService(JournalEntryRepository entries, UserRepository users, DataCipher cipher) {
        this.entries = entries;
        this.users = users;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> list(UUID userId, int page, int size) {
        var p = entries.findByUserId(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(p, EntryResponse::of);
    }

    @Transactional(readOnly = true)
    public EntryResponse get(UUID userId, UUID entryId) {
        return EntryResponse.of(owned(userId, entryId));
    }

    @Transactional
    public EntryResponse create(UUID userId, CreateEntryRequest req) {
        if (entries.existsById(req.id())) {
            throw ApiException.conflict("An entry with this id already exists");
        }
        JournalEntry e = new JournalEntry(req.id(), userId);
        e.setCiphertext(req.ciphertext());
        e.setIv(req.iv());
        e.setReportSentimentEnc(reportSentiment(userId, req.reportSentiment()));
        return EntryResponse.of(entries.saveAndFlush(e));
    }

    @Transactional
    public EntryResponse update(UUID userId, UUID entryId, UpdateEntryRequest req) {
        JournalEntry e = owned(userId, entryId);
        if (!Objects.equals(e.getVersion(), req.version())) {
            throw new OptimisticLockingFailureException("stale version");
        }
        e.setCiphertext(req.ciphertext());
        e.setIv(req.iv());
        e.setReportSentimentEnc(reportSentiment(userId, req.reportSentiment()));
        return EntryResponse.of(entries.saveAndFlush(e));
    }

    @Transactional
    public void delete(UUID userId, UUID entryId) {
        if (entries.deleteOwned(entryId, userId) == 0) {
            throw ApiException.notFound("Entry");
        }
    }

    private JournalEntry owned(UUID userId, UUID entryId) {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow(() -> ApiException.notFound("Entry"));
    }

    /** The mood is kept (server-encrypted) only for users who opted in to the weekly email. */
    private String reportSentiment(UUID userId, Sentiment sentiment) {
        if (sentiment == null) {
            return null;
        }
        boolean optedIn = users.findById(userId).map(User::isWeeklyReport).orElse(false);
        return optedIn ? cipher.encrypt(sentiment.name(), SENTIMENT_FIELD) : null;
    }
}
