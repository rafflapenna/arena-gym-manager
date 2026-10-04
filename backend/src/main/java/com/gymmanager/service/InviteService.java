package com.gymmanager.service;

import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.InviteToken;
import com.gymmanager.repository.InviteTokenRepository;
import com.gymmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Issues password-setup invite tokens and emails the invite link.
 * Shared by member and staff/gym-manager/trainer account creation.
 *
 * <p>Uses a single system SMTP account but sets a per-gym identity (From display name = gym
 * name, Reply-To = gym's contact email → gym manager email → omitted) resolved by
 * {@link GymMailService}, which also owns the actual {@code @Async} send.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final GymMailService gymMailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.url}")
    private String appUrl;

    /**
     * Creates an invite token for the user and hands the email off for delivery.
     * Returns the invite link so the caller can surface it as a fallback
     * (useful while SMTP is not configured).
     *
     * <p>Any still-pending (unused) invite already issued to this user is discarded first, so
     * only the most recently issued link is ever redeemable — an old email lingering in an
     * inbox (or exposed as an API fallback link earlier) can no longer set the password after
     * a fresher one has been issued.
     */
    @Transactional
    public String issueInvite(AppUser user) {
        inviteTokenRepository.deleteByUserAndUsedFalse(user);

        String token = UUID.randomUUID().toString().replace("-", "");
        InviteToken invite = InviteToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();
        inviteTokenRepository.save(invite);

        // Strip trailing slashes: APP_URL="https://host/" would otherwise yield "//set-password",
        // which the SPA router does not match.
        String link = appUrl.replaceAll("/+$", "") + "/set-password?token=" + token;
        sendInviteEmail(user, link);
        return link;
    }

    /**
     * Redeems a pending invite token: validates it (exists, unused, not expired, owning
     * account still active), sets the new password and marks the token used — atomically, so
     * a token can never be left half-consumed (used but password unchanged, or vice versa) if
     * something fails partway through.
     *
     * <p>A deactivated account fails with the exact same "Token non valido o scaduto" error as
     * an invalid/expired token: revealing "this account was disabled" to whoever is holding
     * the link (which may be an old invite/reset email no longer meant to work) would leak
     * account state to someone who isn't necessarily the account owner.
     */
    @Transactional
    public AppUser redeemInvite(String token, String newPassword) {
        InviteToken invite = inviteTokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token non valido o scaduto"));

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token non valido o scaduto");
        }

        AppUser user = invite.getUser();
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token non valido o scaduto");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        invite.setUsed(true);
        inviteTokenRepository.save(invite);

        return user;
    }

    /**
     * Resolves everything the email needs from {@code user}/its {@code gym} while still inside
     * the caller's transaction (lazy fields are safe to touch here), then delegates the actual
     * SMTP round-trip to {@link GymMailService#sendNow} ({@code @Async} under the hood) so this
     * method returns immediately. This matters most for the public forgot-password flow:
     * without it, an existing account (SMTP attempted) and a non-existent one (no send) would
     * have measurably different response times — a timing side-channel despite the identical
     * 204 body.
     */
    private void sendInviteEmail(AppUser user, String link) {
        String body = """
                Benvenuto in Gym Manager!

                Clicca sul link qui sotto per impostare la tua password e accedere all'app.
                Il link è valido per 48 ore.

                %s

                Se non hai richiesto questo accesso, ignora questa email.
                """.formatted(link);

        gymMailService.sendNow(user.getGym(), user.getUsername(), user.getUsername(),
                "Accesso all'app Gym Manager", body);
    }
}
