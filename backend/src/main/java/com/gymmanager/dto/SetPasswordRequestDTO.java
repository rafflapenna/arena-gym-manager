package com.gymmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequestDTO(
        @NotBlank String token,
        @NotBlank @Size(min = 6) String newPassword
) {}
