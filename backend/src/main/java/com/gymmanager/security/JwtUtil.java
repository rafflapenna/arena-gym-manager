package com.gymmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRATION_MS = 28_800_000L; // 8 hours

    /** The weak placeholder that used to ship as a default — must never be used to sign tokens. */
    private static final String INSECURE_DEFAULT =
            "dGhpcyBpcyBhIHZlcnkgc2VjcmV0IGtleSBmb3IgZ3ltIG1hbmFnZXIgYXBwIDIwMjY=";

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank() || secret.equals(INSECURE_DEFAULT)) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing or set to the insecure built-in default. "
                    + "Set a strong, unique base64 JWT_SECRET environment variable before starting the app.");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateToken(String username, String role) {
        return generateToken(username, role, null, null, null, null, false);
    }

    public String generateToken(String username, String role, Long userId, Long memberId,
                                Long gymId, String name, boolean mustChangePassword) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("mustChangePassword", mustChangePassword)
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRATION_MS));
        if (name != null && !name.isBlank()) {
            builder.claim("name", name);
        }
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (memberId != null) {
            builder.claim("memberId", memberId);
        }
        if (gymId != null) {
            builder.claim("gymId", gymId);
        }
        return builder.signWith(key).compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractName(String token) {
        return parseClaims(token).get("name", String.class);
    }

    public Long extractMemberId(String token) {
        return extractLong(token, "memberId");
    }

    public Long extractUserId(String token) {
        return extractLong(token, "userId");
    }

    public Long extractGymId(String token) {
        return extractLong(token, "gymId");
    }

    private Long extractLong(String token, String claim) {
        return longClaim(parseClaims(token), claim);
    }

    private Long longClaim(Claims claims, String claim) {
        Object raw = claims.get(claim);
        if (raw == null) return null;
        if (raw instanceof Long l) return l;
        if (raw instanceof Integer i) return i.longValue();
        return Long.valueOf(raw.toString());
    }

    public boolean extractMustChangePassword(String token) {
        Boolean val = parseClaims(token).get("mustChangePassword", Boolean.class);
        return val != null && val;
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses and validates the token in a single pass: signature check + expiration, returning
     * the {@link Claims} for the caller to read every field off of. Returns {@code null} for any
     * invalid token (bad signature, malformed, expired — {@code parseSignedClaims} already throws
     * {@code ExpiredJwtException} for the latter) instead of throwing, so callers can fall through
     * to "unauthenticated" the same way {@link #isTokenValid} does. Introduced so
     * {@link JwtAuthenticationFilter} parses/verifies the token once per request instead of once
     * per claim (it used to call {@code isTokenValid} + 5 {@code extract*} methods, each re-parsing
     * and re-verifying the HMAC signature).
     */
    public Claims parseIfValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date()) ? claims : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String roleOf(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long userIdOf(Claims claims) {
        return longClaim(claims, "userId");
    }

    public Long memberIdOf(Claims claims) {
        return longClaim(claims, "memberId");
    }

    public Long gymIdOf(Claims claims) {
        return longClaim(claims, "gymId");
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
