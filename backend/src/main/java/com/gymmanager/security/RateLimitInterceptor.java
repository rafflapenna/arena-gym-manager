package com.gymmanager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, dependency-free per-IP rate limiter for forgot-password. Blunts
 * email-flooding on password recovery by capping requests within a fixed window.
 *
 * <p>Login throttling used to live here too (flat per-IP window, counting every
 * request including successful ones) but moved to {@link LoginAttemptService}: a flat
 * per-IP window blocked an entire gym — all behind one shared/NAT'd IP — once a
 * handful of members had logged in. The progressive, failure-only throttle needs
 * per-account state, so it is a proper service rather than an interceptor.
 *
 * <p>State is in-memory and per-instance. With the single backend container in prod
 * this is sufficient; if the backend is ever scaled horizontally, a shared store
 * (e.g. Redis) would be the next step.
 *
 * <p>On limit exceeded it throws {@link ResponseStatusException} 429 — handled by
 * {@code GlobalExceptionHandler} into the usual RFC 9457 ProblemDetail — and sets a
 * {@code Retry-After} header.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Forgot-password: strict — each call may send an email. */
    private static final int FORGOT_MAX = 5;
    private static final long FORGOT_WINDOW_MS = Duration.ofMinutes(15).toMillis();

    /** Safety cap against unbounded growth from rotating/spoofed IPs. */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().endsWith("/auth/forgot-password")) {
            return true; // not a limited endpoint
        }

        long now = System.currentTimeMillis();
        if (counters.size() > MAX_TRACKED_KEYS) {
            counters.clear();
        }

        String key = ClientIpResolver.clientIp(request);
        Window w = counters.compute(key, (k, existing) -> {
            Window cur = (existing == null || now - existing.startMs >= FORGOT_WINDOW_MS)
                    ? new Window(now)
                    : existing;
            cur.count.incrementAndGet();
            return cur;
        });

        if (w.count.get() > FORGOT_MAX) {
            long retryAfterSec = Math.max(1, (FORGOT_WINDOW_MS - (now - w.startMs)) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Troppi tentativi. Riprova tra " + retryAfterSec + " secondi.");
        }
        return true;
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long startMs) { this.startMs = startMs; }
    }
}
