package com.gymmanager.dto;

/** Result of creating a staff/instructor account, with the invite link as fallback. */
public record UserInviteResponseDTO(
        Long id,
        String username,
        String role,
        Long gymId,
        String gymName,
        String inviteLink
) {}
