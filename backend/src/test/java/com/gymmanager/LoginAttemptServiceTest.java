package com.gymmanager;

import com.gymmanager.exception.TooManyAttemptsException;
import com.gymmanager.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Plain unit test (no Spring context, no Testcontainers): {@link LoginAttemptService}
 * only needs a {@link Clock}, which this test fully controls to exercise the
 * progressive-delay math and the 15-minute caps without real sleeping.
 */
class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock);

    private static final String IP = "203.0.113.50";

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void firstThreeFailures_areFree_fourthBlocksFor30Seconds() {
        service.assertNotBlocked("user@example.com", IP); // never failed: not blocked
        failNTimes("user@example.com", IP, 3);
        service.assertNotBlocked("user@example.com", IP); // still free after 3

        service.onFailure("user@example.com", IP); // 4th failure
        assertThat(blockedSeconds("user@example.com", IP)).isEqualTo(30L);
    }

    @Test
    void delayDoubles_onEachSubsequentFailure_andCapsAt15Minutes() {
        // No clock advance between failures: onFailure always overwrites blockedUntilMs
        // unconditionally (a real caller never calls it again while already blocked —
        // assertNotBlocked would have rejected the request first), so this isolates the
        // doubling arithmetic itself from the (also 15-minute) forget-after window, which
        // would otherwise interact with waiting out a 15-minute block for real.
        String user = "doubling@example.com";
        failNTimes(user, IP, 3); // free

        service.onFailure(user, IP); // 4th
        assertThat(blockedSeconds(user, IP)).isEqualTo(30L);
        service.onFailure(user, IP); // 5th
        assertThat(blockedSeconds(user, IP)).isEqualTo(60L);
        service.onFailure(user, IP); // 6th
        assertThat(blockedSeconds(user, IP)).isEqualTo(120L);
        service.onFailure(user, IP); // 7th
        assertThat(blockedSeconds(user, IP)).isEqualTo(240L);
        service.onFailure(user, IP); // 8th
        assertThat(blockedSeconds(user, IP)).isEqualTo(480L);
        service.onFailure(user, IP); // 9th: 960s raw, capped at 15 min
        assertThat(blockedSeconds(user, IP)).isEqualTo(900L);
        service.onFailure(user, IP); // 10th: still capped
        assertThat(blockedSeconds(user, IP)).isEqualTo(900L);
    }

    @Test
    void successfulLogin_resetsTheAccountIpCounter() {
        String user = "resets@example.com";
        failNTimes(user, IP, 4); // 4th failure -> blocked for 30s
        clock.advance(Duration.ofSeconds(31));
        service.onSuccess(user, IP);

        // Counter is back to zero: 3 more failures are free again, the 4th blocks at 30s again.
        failNTimes(user, IP, 3);
        service.assertNotBlocked(user, IP);
        service.onFailure(user, IP);
        assertThat(blockedSeconds(user, IP)).isEqualTo(30L);
    }

    @Test
    void accountIpBlock_doesNotAffectTheSameAccountOnAnotherIp() {
        String user = "sameuser@example.com";
        String otherIp = "203.0.113.51";
        failNTimes(user, IP, 4); // blocked on IP

        assertThatThrownBy(() -> service.assertNotBlocked(user, IP)).isInstanceOf(TooManyAttemptsException.class);
        service.assertNotBlocked(user, otherIp); // different IP: untouched
    }

    @Test
    void thirtyFailuresAcrossDifferentAccounts_onTheSameIp_blocksThatIp() {
        String ip = "203.0.113.52";
        for (int i = 0; i < 29; i++) {
            service.onFailure("account" + i + "@example.com", ip);
        }
        // Still under the per-account+IP free-attempt cap (1 failure each) AND under the
        // IP-wide cap (29 < 30): a brand-new account on this IP is not blocked yet.
        service.assertNotBlocked("freshAccount@example.com", ip);

        service.onFailure("account29@example.com", ip); // 30th failure on this IP
        assertThat(blockedSeconds("anotherFreshAccount@example.com", ip)).isEqualTo(600L); // 10 min
    }

    @Test
    void successfulLoginsOnTheSameIp_doNotCountTowardsTheIpWideFailureWindow() {
        String ip = "203.0.113.53";
        for (int i = 0; i < 29; i++) {
            service.onFailure("account" + i + "@example.com", ip);
        }
        // A burst of successful logins on the same IP: must not push the IP over the edge,
        // and must not reset the 29 failures already counted either.
        for (int i = 0; i < 50; i++) {
            service.onSuccess("success" + i + "@example.com", ip);
        }
        service.assertNotBlocked("stillFreeAccount@example.com", ip);

        // The 30th real failure still lands on top of the untouched count and blocks the IP.
        service.onFailure("account29@example.com", ip);
        assertThatThrownBy(() -> service.assertNotBlocked("yetAnotherAccount@example.com", ip))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void hundredConsecutiveFailures_onTheSameAccount_blocksTheAccountAcrossAnyIp() {
        String user = "bruteforced@example.com";
        // Spread across distinct IPs so neither the per-account+IP nor the per-IP counters
        // trip first — only the per-account (any IP) counter should be responsible.
        for (int i = 0; i < 99; i++) {
            service.onFailure(user, "198.51.100." + (i % 250));
        }
        service.assertNotBlocked(user, "203.0.113.99"); // still 99: not blocked yet

        service.onFailure(user, "203.0.113.99"); // 100th consecutive failure
        assertThat(blockedSeconds(user, "203.0.113.100")).isEqualTo(900L); // 15 minutes, any IP

        // A successful login clears it even checked from yet another IP.
        service.onSuccess(user, "203.0.113.101");
        service.assertNotBlocked(user, "203.0.113.102");
    }

    @Test
    void retryAfterSeconds_isTheCeilingOfTheExactRemainingTime() {
        String user = "precise@example.com";
        failNTimes(user, IP, 4); // blocked for exactly 30s from now

        clock.advance(Duration.ofSeconds(10));
        assertThat(blockedSeconds(user, IP)).isEqualTo(20L);

        clock.advance(Duration.ofMillis(19_500)); // 19.5s further: 500ms left, rounds up to 1s
        assertThat(blockedSeconds(user, IP)).isEqualTo(1L);
    }

    @Test
    void nonExistentUsername_isThrottledIdenticallyToAnExistingOne() {
        // LoginAttemptService has no notion of whether an account exists — it is purely a
        // function of (normalized username, IP), so a never-registered address and a real
        // one follow byte-for-byte the same schedule (anti user-enumeration).
        String ghost = "ghost@nowhere.example";
        String real = "real@example.com";
        String ghostIp = "203.0.113.60";
        String realIp = "203.0.113.61";

        failNTimes(ghost, ghostIp, 4);
        failNTimes(real, realIp, 4);

        assertThat(blockedSeconds(ghost, ghostIp)).isEqualTo(blockedSeconds(real, realIp)).isEqualTo(30L);
    }

    @Test
    void usernameNormalization_isCaseAndWhitespaceInsensitive() {
        failNTimes("Padded@Example.com  ", IP, 4);
        assertThatThrownBy(() -> service.assertNotBlocked("  padded@example.COM", IP))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    /** Asserts the next {@code assertNotBlocked} call throws and returns its retryAfterSeconds. */
    private long blockedSeconds(String username, String ip) {
        try {
            service.assertNotBlocked(username, ip);
        } catch (TooManyAttemptsException ex) {
            return ex.getRetryAfterSeconds();
        }
        fail("expected " + username + "@" + ip + " to be blocked");
        return -1; // unreachable: fail() always throws
    }

    private void failNTimes(String username, String ip, int n) {
        for (int i = 0; i < n; i++) {
            service.onFailure(username, ip);
        }
    }

    /** Mutable {@link Clock} test double: starts at a fixed instant, advances only on demand. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
