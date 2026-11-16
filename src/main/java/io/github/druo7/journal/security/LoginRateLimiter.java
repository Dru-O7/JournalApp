package io.github.druo7.journal.security;

import io.github.druo7.journal.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fixed-window limit on failed logins, per username and per client IP. In-memory, so the limit is
 * per instance; that is adequate for a single-instance deployment. Use a shared store (e.g. Redis)
 * if the app is scaled horizontally.
 */
@Component
public class LoginRateLimiter {
    private static final int MAX_TRACKED_KEYS = 100_000;

    private record Window(Instant start, int failures) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AppProperties.LoginRateLimit cfg;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LoginRateLimiter(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    LoginRateLimiter(AppProperties props, Clock clock) {
        this.cfg = props.loginRateLimit();
        this.clock = clock;
    }

    public boolean isBlocked(String username, String ip) {
        return failures(userKey(username)) >= cfg.maxAttemptsPerUsername()
                || failures(ipKey(ip)) >= cfg.maxAttemptsPerIp();
    }

    public void recordFailure(String username, String ip) {
        if (windows.size() > MAX_TRACKED_KEYS) {
            evictExpired();
        }
        increment(userKey(username));
        increment(ipKey(ip));
    }

    public void recordSuccess(String username) {
        windows.remove(userKey(username));
    }

    private int failures(String key) {
        Window w = windows.get(key);
        return (w == null || expired(w)) ? 0 : w.failures();
    }

    private void increment(String key) {
        windows.compute(key, (k, w) -> (w == null || expired(w))
                ? new Window(clock.instant(), 1)
                : new Window(w.start(), w.failures() + 1));
    }

    private boolean expired(Window w) {
        return Duration.between(w.start(), clock.instant()).compareTo(cfg.window()) >= 0;
    }

    private void evictExpired() {
        windows.entrySet().removeIf(e -> expired(e.getValue()));
    }

    private static String userKey(String username) {
        return "u:" + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
