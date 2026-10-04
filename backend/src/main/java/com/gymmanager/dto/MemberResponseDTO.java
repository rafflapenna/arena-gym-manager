package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberResponseDTO {
    private Long id;
    private PersonalInfoResponseDTO personalInfo;
    private LocalDate registrationDate;
    private Boolean active;
    private String notes;
    /** {id, name} of the member who brought this one in; null if nobody did. */
    private NamedRefDTO referredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
