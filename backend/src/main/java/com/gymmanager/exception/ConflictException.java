package com.gymmanager.exception;

import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {

    /**
     * Machine-readable code for the FE to branch on (e.g. {@code LEAD_DUPLICATE_PHONE}),
     * surfaced as the {@code code} property on the 409 ProblemDetail. Null for every
     * pre-existing 409 that only ever carried a human-readable message.
     */
    private final String code;

    public ConflictException(String message) {
        super(message);
        this.code = null;
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }
}
