package com.gymmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Update an instructor/staff account. Email is optional: if provided and
 * changed, it becomes the new login username (must be unique).
 */
public record UpdateUserRequestDTO(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email String email
) {}
