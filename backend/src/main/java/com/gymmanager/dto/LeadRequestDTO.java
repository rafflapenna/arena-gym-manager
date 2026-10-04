package com.gymmanager.dto;

import com.gymmanager.entity.LeadStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeadRequestDTO {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    @Size(max = 100)
    private String source;

    /** Optional: must reference a Course in the same gym (validated in service). */
    private Long courseId;

    private LocalDate trialDate;

    /** Optional: defaulted by the service from trialDate when omitted (see LeadService.deriveStatus). */
    private LeadStatus status;

    private String notes;

    /** Optional: the member who brought this prospect in; carried onto the Member at convert. */
    private Long referredByMemberId;
}
