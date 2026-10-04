package com.gymmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateTrainerRequestDTO(
        @NotBlank @Email String email,
        String firstName,
        String lastName
) {}
