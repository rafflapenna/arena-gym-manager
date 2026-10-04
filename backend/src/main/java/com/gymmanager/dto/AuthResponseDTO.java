package com.gymmanager.dto;

public record AuthResponseDTO(
        String token,
        String username,
        String name,
        String role,
        Long memberId,
        Long gymId,
        boolean mustChangePassword
) {}
