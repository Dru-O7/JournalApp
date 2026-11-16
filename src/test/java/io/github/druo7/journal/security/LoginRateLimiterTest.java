package io.github.druo7.journal.security;

import io.github.druo7.journal.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(new AppProperties(
            new AppProperties.Jwt("x", Duration.ofMinutes(1), "i"), "k", new AppProperties.Cors(List.of()),
            new AppProperties.Weather("u", "", "c"), new AppProperties.Quote("q"),
            new AppProperties.LoginRateLimit(3, 5, Duration.ofMinutes(15))), clock);

    @Test
    void blocksUsernameAfterLimitCaseInsensitively() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("Alice", "10.0.0." + i);
        }
        assertThat(limiter.isBlocked("alice", "10.0.0.99")).isTrue();
        assertThat(limiter.isBlocked("bob", "10.0.0.99")).isFalse();
    }

    @Test
    void blocksIpAcrossUsernames() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("user" + i, "1.2.3.4");
        }
        assertThat(limiter.isBlocked("fresh", "1.2.3.4")).isTrue();
        assertThat(limiter.isBlocked("fresh", "5.6.7.8")).isFalse();
    }

    @Test
    void windowExpires() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("carol", "ip");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        assertThat(limiter.isBlocked("carol", "ip")).isFalse();
    }

    @Test
    void successResetsUsernameCounter() {
        limiter.recordFailure("dave", "ip");
        limiter.recordFailure("dave", "ip");
        limiter.recordSuccess("dave");
        limiter.recordFailure("dave", "ip");
        assertThat(limiter.isBlocked("dave", "ip")).isFalse();
    }
}
