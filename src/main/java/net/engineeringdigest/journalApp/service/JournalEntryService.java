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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * A daily journal. Entries are opaque ciphertext to this service; what it enforces is the shape of
 * the journal:
 * <ul>
 *   <li>one entry per user per calendar day, in the writer's time zone;</li>
 *   <li>an entry can be edited only until its day ends, then it is read-only;</li>
 *   <li>entries cannot be deleted (deleting the account removes them all).</li>
 * </ul>
 * Every operation is scoped to the caller's user id; a foreign or missing id yields the same 404.
 */
@Service
public class JournalEntryService {
    public static final String SENTIMENT_FIELD = "journal_entries.report_sentiment";

    /** How far back the activity heatmap reaches: 53 weeks plus a margin. */
    static final int ACTIVITY_DAYS = 380;

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
        Instant now = Instant.now();
        var p = entries.findByUserId(userId, PageRequest.of(page, size, Sort.by("entryDate").descending()));
        return PageResponse.of(p, e -> EntryResponse.of(e, now));
    }

    @Transactional(readOnly = true)
    public ActivityResponse activity(UUID userId) {
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(ACTIVITY_DAYS);
        return new ActivityResponse(entries.findEntryDatesSince(userId, since));
    }

    @Transactional(readOnly = true)
    public EntryResponse get(UUID userId, UUID entryId) {
        return EntryResponse.of(owned(userId, entryId), Instant.now());
    }

    @Transactional
    public EntryResponse create(UUID userId, CreateEntryRequest req) {
        ZoneId zone = zone(req.timeZone());
        LocalDate today = LocalDate.now(zone);
        if (entries.existsById(req.id())) {
            throw ApiException.conflict("An entry with this id already exists");
        }
        if (entries.existsByUserIdAndEntryDate(userId, today)) {
            throw ApiException.conflict("You have already written today's entry");
        }
        JournalEntry e = new JournalEntry(req.id(), userId);
        e.setEntryDate(today);
        e.setLocksAt(today.plusDays(1).atStartOfDay(zone).toInstant());
        e.setCiphertext(req.ciphertext());
        e.setIv(req.iv());
        e.setReportSentimentEnc(reportSentiment(userId, req.reportSentiment()));
        // A concurrent create for the same day hits the unique constraint and is reported as 409.
        return EntryResponse.of(entries.saveAndFlush(e), Instant.now());
    }

    @Transactional
    public EntryResponse update(UUID userId, UUID entryId, UpdateEntryRequest req) {
        JournalEntry e = owned(userId, entryId);
        Instant now = Instant.now();
        if (!e.isEditable(now)) {
            throw ApiException.forbidden("This day has ended, so its entry can no longer be changed");
        }
        if (!Objects.equals(e.getVersion(), req.version())) {
            throw new OptimisticLockingFailureException("stale version");
        }
        e.setCiphertext(req.ciphertext());
        e.setIv(req.iv());
        e.setReportSentimentEnc(reportSentiment(userId, req.reportSentiment()));
        return EntryResponse.of(entries.saveAndFlush(e), now);
    }

    private JournalEntry owned(UUID userId, UUID entryId) {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow(() -> ApiException.notFound("Entry"));
    }

    /** Only named zones: they are unambiguous, and "today" is meant as the writer's local day. */
    private static ZoneId zone(String timeZone) {
        if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw ApiException.badRequest("Unknown time zone; use an IANA name such as Europe/Berlin");
        }
        return ZoneId.of(timeZone);
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
