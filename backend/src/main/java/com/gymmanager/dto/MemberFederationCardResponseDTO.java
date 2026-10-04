package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberFederationCardResponseDTO {
    private NamedRefDTO federation;
    private String cardNumber;
}
