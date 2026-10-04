package com.gymmanager.service;

import com.gymmanager.entity.AppUser;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Self-service password recovery for non-ADMIN accounts.
 * Intentionally returns no information about whether the email exists
 * (anti user-enumeration: the response is always identical).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryService {

    private final UserRepository userRepository;
    private final InviteService inviteService;

    /**
     * Emits a password-reset token (via InviteService) when all of the following hold:
     * <ul>
     *   <li>The email maps to an existing account.</li>
     *   <li>The account is not an ADMIN.</li>
     *   <li>The account is active.</li>
     * </ul>
     * In all other cases the method returns silently — the caller always gets the same
     * 204 response, so callers cannot infer whether the email is registered.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = email.toLowerCase().trim();

        Optional<AppUser> maybeUser = userRepository.findByUsernameIgnoreCase(normalized);
        if (maybeUser.isEmpty()) {
            log.debug("forgot-password: no account for '{}' — silent no-op", normalized);
            return;
        }

        AppUser user = maybeUser.get();

        if (Roles.ADMIN.equals(user.getRole())) {
            log.debug("forgot-password: account '{}' is ADMIN — silent no-op", normalized);
            return;
        }

        if (!user.isActive()) {
            log.debug("forgot-password: account '{}' is inactive — silent no-op", normalized);
            return;
        }

        // issueInvite persists the token and hands the email off to MailerService (@Async):
        // the SMTP round-trip runs off-thread, so this method — and therefore the public
        // /forgot-password 204 — returns in constant time whether or not the account exists
        // (SMTP failures are swallowed inside MailerService regardless).
        // The link is NOT returned to the caller: this is a public self-service flow.
        inviteService.issueInvite(user);
        log.info("forgot-password: reset token issued for '{}'", normalized);
    }
}
