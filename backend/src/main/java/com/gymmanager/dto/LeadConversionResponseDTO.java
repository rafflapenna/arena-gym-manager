package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

/** Response of {@code POST /leads/{id}/convert}: the updated lead plus the member it now points to. */
@Data
@Builder
public class LeadConversionResponseDTO {
    private LeadResponseDTO lead;
    private MemberResponseDTO member;
}
