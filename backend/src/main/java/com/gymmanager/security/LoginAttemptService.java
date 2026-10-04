package com.gymmanager.security;

import com.gymmanager.exception.TooManyAttemptsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progressive login throttling that counts only FAILED attempts (OWASP Authentication
 * Cheat Sheet / NIST SP 800-63B §5.2.2 / Auth0 recommendations), replacing the old flat
 * per-IP window that also counted successful logins — which, behind a gym's single
 * shared/NAT'd IP, blocked everyone once a handful of members had logged in.
 *
 * <p>Three independent counters, all keyed on the normalized (lowercase/trim) username
 * so behavior is identical for existing and non-existing accounts (anti-enumeration):
 * <ol>
 *   <li><b>account + IP</b> — first 3 failures free, then an exponentially doubling
 *       block (30s, 60s, 120s, 240s, ... capped at 15 min); a successful login or 15
 *       minutes without a failure resets it.</li>
 *   <li><b>IP only</b> (all accounts) — 30 failures in a 10-minute window blocks that
 *       IP for 10 minutes; successful logins never count towards this.</li>
 *   <li><b>account only</b> (any IP) — 100 consecutive failures blocks the account for
 *       15 minutes (NIST); only a successful login resets it.</li>
 * </ol>
 *
 * <p>While any of the three is blocking, the caller must reject the request with 429
 * <em>without</em> checking the password and without counting the attempt again —
 * {@link #assertNotBlocked} does exactly that check-only step.
 *
 * <p>State is in-memory ({@code ConcurrentHashMap}) and per-instance — same caveat as
 * {@link RateLimitInterceptor}: fine with the single backend container in prod, would
 * need a shared store (Redis) if ever scaled horizontally. Each map has a safety cap
 * against unbounded growth (rotating/spoofed IPs or usernames) plus a periodic sweep of
 * stale, unblocked entries to keep normal operation lean.
 */
@Service
public class LoginAttemptService {

    /** Account+IP: attempts 1-3 are free. */
    private static final int ACCOUNT_IP_FREE_ATTEMPTS = 3;
    /** Account+IP: 4th failure blocks for this long, doubling each failure after. */
    private static final Duration ACCOUNT_IP_BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration ACCOUNT_IP_MAX_DELAY = Duration.ofMinutes(15);
    /** Account+IP: no failure for this long forgets the streak entirely. */
    private static final Duration ACCOUNT_IP_FORGET_AFTER = Duration.ofMinutes(15);

    /** IP (all accounts): this many failures within the window blocks the IP. */
    private static final int IP_MAX_FAILURES = 30;
    private static final Duration IP_WINDOW = Duration.ofMinutes(10);
    private static final Duration IP_BLOCK_DURATION = Duration.ofMinutes(10);

    /** Account (any IP): this many *consecutive* failures blocks the account (NIST). */
    private static final int ACCOUNT_MAX_FAILURES = 100;
    private static final Duration ACCOUNT_BLOCK_DURATION = Duration.ofMinutes(15);

    /** Safety cap per map against unbounded growth; same blunt strategy as RateLimitInterceptor. */
    private static final int MAX_TRACKED_KEYS = 50_000;
    /** Scheduled sweep: entries idle this long with no active block are dropped. */
    private static final Duration SWEEP_IDLE_AFTER = Duration.ofHours(24);

    private final Clock clock;

    private final Map<String, AccountIpAttempt> accountIpAttempts = new ConcurrentHashMap<>();
    private final Map<String, IpWindowAttempt> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, AccountAttempt> accountAttempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Call BEFORE authenticating. Throws {@link TooManyAttemptsException} (429) if the
     * account+IP, the IP, or the account is currently blocked — the caller must not
     * check the password nor call {@link #onFailure}/{@link #onSuccess} in that case.
     */
    public void assertNotBlocked(String username, String ip) {
        String user = normalize(username);
        long now = clock.millis();
        long remainingMs = 0;

        AccountIpAttempt a = accountIpAttempts.get(accountIpKey(user, ip));
        if (a != null) {
            remainingMs = Math.max(remainingMs, a.blockedUntilMs - now);
        }
        IpWindowAttempt w = ipAttempts.get(ip);
        if (w != null) {
            remainingMs = Math.max(remainingMs, w.blockedUntilMs - now);
        }
        AccountAttempt acc = accountAttempts.get(user);
        if (acc != null) {
            remainingMs = Math.max(remainingMs, acc.blockedUntilMs - now);
        }

        if (remainingMs > 0) {
            throw new TooManyAttemptsException(ceilSeconds(remainingMs));
        }
    }

    /** Call after a failed authentication (wrong password, unknown or inactive username). */
    public void onFailure(String username, String ip) {
        String user = normalize(username);
        long now = clock.millis();

        accountIpAttempts.compute(accountIpKey(user, ip), (k, existing) -> {
            AccountIpAttempt a = existing != null ? existing : new AccountIpAttempt();
            if (a.lastFailureMs != 0 && now - a.lastFailureMs >= ACCOUNT_IP_FORGET_AFTER.toMillis()) {
                a.consecutiveFailures = 0;
                a.blockedUntilMs = 0;
            }
            a.consecutiveFailures++;
            a.lastFailureMs = now;
            if (a.consecutiveFailures > ACCOUNT_IP_FREE_ATTEMPTS) {
                int doublings = a.consecutiveFailures - ACCOUNT_IP_FREE_ATTEMPTS - 1;
                long delayMs = doublings >= 20 // guard against shifting past 63 bits on a pathological streak
                        ? ACCOUNT_IP_MAX_DELAY.toMillis()
                        : Math.min(ACCOUNT_IP_BASE_DELAY.toMillis() * (1L << doublings), ACCOUNT_IP_MAX_DELAY.toMillis());
                a.blockedUntilMs = now + delayMs;
            }
            return a;
        });
        capIfOversized(accountIpAttempts);

        ipAttempts.compute(ip, (k, existing) -> {
            IpWindowAttempt w = existing != null ? existing : new IpWindowAttempt();
            if (w.windowStartMs == 0 || now - w.windowStartMs >= IP_WINDOW.toMillis()) {
                w.windowStartMs = now;
                w.failuresInWindow = 0;
            }
            w.failuresInWindow++;
            if (w.failuresInWindow >= IP_MAX_FAILURES) {
                w.blockedUntilMs = now + IP_BLOCK_DURATION.toMillis();
                // Fresh window so the block doesn't instantly re-trigger once it expires.
                w.windowStartMs = now;
                w.failuresInWindow = 0;
            }
            return w;
        });
        capIfOversized(ipAttempts);

        accountAttempts.compute(user, (k, existing) -> {
            AccountAttempt acc = existing != null ? existing : new AccountAttempt();
            acc.consecutiveFailures++;
            acc.lastFailureMs = now;
            if (acc.consecutiveFailures >= ACCOUNT_MAX_FAILURES) {
                acc.blockedUntilMs = now + ACCOUNT_BLOCK_DURATION.toMillis();
            }
            return acc;
        });
        capIfOversized(accountAttempts);
    }

    /** Call after a successful authentication: resets the account+IP and account-wide counters. */
    public void onSuccess(String username, String ip) {
        String user = normalize(username);
        accountIpAttempts.remove(accountIpKey(user, ip));
        accountAttempts.remove(user);
        // ipAttempts (per-IP, all accounts) is intentionally NOT cleared: successful logins
        // from other accounts on the same IP must not erase that IP's failure history.
    }

    /**
     * Best-effort periodic cleanup of stale, currently-unblocked entries — memory hygiene
     * only, not security-critical (the per-map size cap in {@link #capIfOversized} is what
     * actually bounds worst-case growth).
     */
    @Scheduled(fixedRate = 5 * 60 * 1000L)
    public void sweepStaleEntries() {
        long now = clock.millis();
        accountIpAttempts.entrySet().removeIf(e -> {
            AccountIpAttempt a = e.getValue();
            return a.blockedUntilMs <= now && now - a.lastFailureMs >= SWEEP_IDLE_AFTER.toMillis();
        });
        ipAttempts.entrySet().removeIf(e -> {
            IpWindowAttempt w = e.getValue();
            return w.blockedUntilMs <= now && now - w.windowStartMs >= SWEEP_IDLE_AFTER.toMillis();
        });
        accountAttempts.entrySet().removeIf(e -> {
            AccountAttempt a = e.getValue();
            return a.blockedUntilMs <= now && now - a.lastFailureMs >= SWEEP_IDLE_AFTER.toMillis();
        });
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String accountIpKey(String normalizedUsername, String ip) {
        return normalizedUsername + "|" + ip;
    }

    private static long ceilSeconds(long ms) {
        return Math.max(1, (ms + 999) / 1000);
    }

    private static void capIfOversized(Map<String, ?> map) {
        if (map.size() > MAX_TRACKED_KEYS) {
            map.clear();
        }
    }

    private static final class AccountIpAttempt {
        int consecutiveFailures;
        long lastFailureMs;
        long blockedUntilMs;
    }

    private static final class IpWindowAttempt {
        long windowStartMs;
        int failuresInWindow;
        long blockedUntilMs;
    }

    private static final class AccountAttempt {
        int consecutiveFailures;
        long lastFailureMs;
        long blockedUntilMs;
    }
}
