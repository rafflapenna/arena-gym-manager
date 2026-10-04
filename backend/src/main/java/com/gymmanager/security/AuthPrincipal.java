package com.gymmanager.security;

/**
 * Lightweight authenticated-user context attached to the Spring Security
 * Authentication details. Built from JWT claims on every request.
 *
 * @param userId   the AppUser id
 * @param memberId the linked Member id (only for MEMBER accounts), may be null
 * @param gymId    the gym (tenant) the user belongs to; null for ADMIN
 * @param role     the role without the ROLE_ prefix (ADMIN/GYM_MANAGER/STAFF/TRAINER/MEMBER)
 */
public record AuthPrincipal(Long userId, Long memberId, Long gymId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
