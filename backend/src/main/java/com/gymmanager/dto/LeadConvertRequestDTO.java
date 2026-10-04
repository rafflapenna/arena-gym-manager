package com.gymmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Body of {@code POST /leads/{id}/convert}: the operator types the email, plus, for a
 * MEMBER-origin lead the reception hasn't completed yet, the birth date the lead itself is
 * still missing ({@link com.gymmanager.entity.Lead#getBirthDate()} null) — everything else
 * needed to create the {@link com.gymmanager.entity.Member} already lives on the lead.
 */
@Data
public class LeadConvertRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    /**
     * Only used when the lead has no birth date of its own; ignored (and the lead's own value
     * kept) otherwise. Missing in both places -> 422 {@code errors.birthDate}.
     */
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;
}
