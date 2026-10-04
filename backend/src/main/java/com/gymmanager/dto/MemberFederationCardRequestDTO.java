package com.gymmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberFederationCardRequestDTO {
    @NotBlank
    @Size(max = 50)
    private String cardNumber;
}
