package com.gymmanager.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind the Caddy reverse proxy. Shared by every
 * auth-endpoint throttle ({@link LoginAttemptService} for login, {@link RateLimitInterceptor}
 * for forgot-password) so the "which hop do we trust" logic lives in exactly one place.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Real client IP behind the Caddy reverse proxy (rightmost hop of X-Forwarded-For).
     *
     * <p>Caddy appends the real peer IP to the end of {@code X-Forwarded-For} before
     * proxying to this backend. The rightmost value is therefore the IP our trusted
     * proxy observed and cannot be forged by the client; any values to its left
     * (including a spoofed header sent by the client itself) are attacker-controlled
     * and must be ignored — otherwise a client could vary the header on every request
     * to land in a different rate-limit bucket and bypass the limiter entirely.
     */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.lastIndexOf(',');
            return (comma >= 0 ? xff.substring(comma + 1) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
