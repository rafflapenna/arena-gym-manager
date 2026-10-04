package com.gymmanager.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Body of {@code POST /me/leads} — a member reporting a friend's free trial ("prove inserite
 * dai soci", 2026-10-01). Deliberately narrower than the staff-side {@link LeadRequestDTO}: no
 * birthDate/email/notes/status/referredByMemberId (the last is forced server-side to the
 * caller, never trusted from the body — any such extra field is simply ignored by Jackson).
 */
@Data
public class MeLeadRequestDTO {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    /** Optional: a proposed trial date; the reception can change it on confirm. */
    @FutureOrPresent(message = "Trial date must be today or in the future")
    private LocalDate trialDate;

    /** Optional: must reference a Course in the member's own gym (validated in service). */
    private Long courseId;
}
