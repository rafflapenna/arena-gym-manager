package com.gymmanager.dto;

import lombok.Builder;

@Builder
public record UserResponseDTO(
        Long id,
        String username,
        String firstName,
        String lastName,
        String role,
        boolean active,
        boolean mustChangePassword,
        Long gymId,
        String gymName,
        Long memberId
) {}
