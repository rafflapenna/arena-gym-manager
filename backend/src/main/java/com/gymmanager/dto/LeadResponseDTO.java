package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeadResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String source;
    private NamedRefDTO interestCourse;
    private LocalDate trialDate;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private Long convertedMemberId;
    /** {id, name} of the member who brought this prospect in; null if nobody did. */
    private NamedRefDTO referredBy;
    /** "STAFF" or "MEMBER" — who created this lead (see {@link com.gymmanager.entity.LeadOrigin}). */
    private String origin;
    /**
     * Logical reference to an existing member whose phone matches this lead's, or null.
     * Staff-only (anti-enumeration: never exposed on {@code MeLeadResponseDTO}).
     */
    private Long matchedMemberId;
}
