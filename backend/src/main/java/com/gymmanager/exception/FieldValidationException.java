package com.gymmanager.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Field-level 422, same shape as {@code MethodArgumentNotValidException} (title "Validation
 * error", {@code errors} map property) but raised from service code for a check bean
 * validation can't express — e.g. "you can't report your own phone number" in
 * {@code MeLeadService}, which needs the logged-in member's own data to decide.
 */
@Getter
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super("Invalid input data");
        this.errors = errors;
    }
}
