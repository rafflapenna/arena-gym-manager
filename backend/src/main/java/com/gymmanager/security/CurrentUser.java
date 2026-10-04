package com.gymmanager.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Convenience accessor for the {@link AuthPrincipal} of the current request.
 */
@Component
public class CurrentUser {

    public AuthPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (auth.getDetails() instanceof AuthPrincipal principal) {
            return principal;
        }
        // Fallback for non-JWT authentication (e.g. tests with @WithMockUser):
        // derive the role from authorities; no gym/member context.
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("");
        return new AuthPrincipal(null, null, null, role);
    }

    public Long gymId() {
        return get().gymId();
    }

    public Long memberId() {
        return get().memberId();
    }

    public boolean isAdmin() {
        return get().isAdmin();
    }

    /** ADMIN can access any gym; others only their own. */
    public boolean canAccessGym(Long targetGymId) {
        if (isAdmin()) return true;
        return targetGymId != null && targetGymId.equals(gymId());
    }
}
