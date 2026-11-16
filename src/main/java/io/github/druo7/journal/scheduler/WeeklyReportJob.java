package io.github.druo7.journal.scheduler;

import lombok.extern.slf4j.Slf4j;
import io.github.druo7.journal.crypto.DataCipher;
import io.github.druo7.journal.entity.Sentiment;
import io.github.druo7.journal.entity.User;
import io.github.druo7.journal.repository.JournalEntryRepository;
import io.github.druo7.journal.repository.UserRepository;
import io.github.druo7.journal.service.EmailService;
import io.github.druo7.journal.service.JournalEntryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Weekly mood email for users who opted in. Only the mood (never journal text) is available to the
 * server, and only for opted-in users. One user's failure never stops the others.
 */
@Slf4j
@Component
public class WeeklyReportJob {
    private static final int PAGE_SIZE = 100;

    private final UserRepository users;
    private final JournalEntryRepository entries;
    private final DataCipher cipher;
    private final EmailService email;

    public WeeklyReportJob(UserRepository users, JournalEntryRepository entries, DataCipher cipher, EmailService email) {
        this.users = users;
        this.entries = entries;
        this.cipher = cipher;
        this.email = email;
    }

    @Scheduled(cron = "${app.weekly-report.cron:0 0 9 * * SUN}", zone = "${app.weekly-report.zone:UTC}")
    @SchedulerLock(name = "weeklyReport")
    public void run() {
        Instant since = Instant.now().minus(Duration.ofDays(7));
        int sent = 0;
        int failed = 0;
        Page<User> page;
        int pageNo = 0;
        do {
            page = users.findByWeeklyReportTrue(PageRequest.of(pageNo++, PAGE_SIZE, Sort.by("id")));
            for (User user : page) {
                try {
                    if (sendFor(user, since)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Weekly report failed for user {}", user.getId(), e);
                }
            }
        } while (page.hasNext());
        log.info("Weekly report finished: {} sent, {} failed", sent, failed);
    }

    boolean sendFor(User user, Instant since) {
        String to = cipher.decrypt(user.getEmailEnc(), io.github.druo7.journal.service.UserService.EMAIL_FIELD);
        if (to == null) {
            return false;
        }
        List<Sentiment> moods = entries.findReportSentimentsSince(user.getId(), since).stream()
                .map(s -> Sentiment.valueOf(cipher.decrypt(s, JournalEntryService.SENTIMENT_FIELD)))
                .toList();
        Optional<Sentiment> top = mostFrequent(moods);
        if (top.isEmpty()) {
            return false;
        }
        email.send(to, "Your weekly mood summary", body(user.getUsername(), top.get(), moods));
        return true;
    }

    /** Most frequent mood; ties go to the earlier constant in {@link Sentiment} so the result is deterministic. */
    static Optional<Sentiment> mostFrequent(List<Sentiment> moods) {
        EnumMap<Sentiment, Integer> counts = new EnumMap<>(Sentiment.class);
        moods.stream().filter(Objects::nonNull).forEach(m -> counts.merge(m, 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Sentiment, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey);
    }

    private static String body(String username, Sentiment top, List<Sentiment> moods) {
        StringBuilder sb = new StringBuilder()
                .append("Hi ").append(username).append(",\n\n")
                .append("Over the last 7 days your most frequent mood was ").append(top.name().toLowerCase(Locale.ROOT)).append(".\n\n");
        EnumMap<Sentiment, Long> counts = new EnumMap<>(Sentiment.class);
        moods.forEach(m -> counts.merge(m, 1L, Long::sum));
        counts.forEach((k, v) -> sb.append("  ").append(k.name().toLowerCase(Locale.ROOT)).append(": ").append(v).append('\n'));
        sb.append("\nYou can turn this email off in Settings.\n");
        return sb.toString();
    }
}
