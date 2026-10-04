package com.gymmanager.exception;

import lombok.Getter;

/**
 * Thrown by {@link com.gymmanager.security.LoginAttemptService} when the login throttle
 * (per account+IP, per IP, or per account) is currently active. Carries the exact
 * remaining seconds so the handler can set {@code Retry-After} and the {@code
 * retryAfterSeconds} ProblemDetail property the FE polls on.
 */
@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Troppi tentativi. Riprova tra " + retryAfterSeconds + " secondi.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
