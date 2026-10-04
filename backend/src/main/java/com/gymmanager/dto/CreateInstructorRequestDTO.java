package com.gymmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInstructorRequestDTO(
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        @NotNull Long gymId
) {}
