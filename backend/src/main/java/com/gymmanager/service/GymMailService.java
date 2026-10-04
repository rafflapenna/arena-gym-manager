package com.gymmanager.service;

import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.EmailCategory;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.EmailOutboxRepository;
import com.gymmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared per-gym mail identity, and the two ways a message leaves the system:
 * {@link #sendNow} for the handful of flows where the recipient is actively waiting (invites —
 * unchanged, still {@code @Async} via {@link MailerService}), and {@link #enqueue} for
 * everything else, which lands in the {@code email_outbox} and is delivered later by
 * {@code EmailOutboxDrainJob}.
 *
 * <p>Reply-To resolution (used by both paths) is the 3-level priority {@code InviteService} had
 * before this class existed — moved here verbatim so it's no longer duplicated:
 * <ol>
 *   <li>{@code gym.contactEmail}</li>
 *   <li>the gym's GYM_MANAGER (skipped if that's the recipient themselves)</li>
 *   <li>no Reply-To header</li>
 * </ol>
 * {@code MedicalCertReminderJob} previously stopped at level 1 (gym.contactEmail only) — routing
 * it through here is this change's one observable behavior difference in prod (see
 * CLAUDE.md / DESIGN-scheduler-email.it.md).
 */
@Service
@RequiredArgsConstructor
public class GymMailService {

    private final UserRepository userRepository;
    private final EmailOutboxRepository outboxRepository;
    private final MailerService mailerService;

    @Value("${app.mail.from:}")
    private String appMailFrom;

    @Value("${app.mail.from-name:Gym Manager}")
    private String appMailFromName;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    /** Resolved From/Reply-To for a gym, computed once per send/enqueue. */
    public record Identity(String fromAddress, String fromDisplayName, String replyTo) {}

    public Identity resolveIdentity(Gym gym, String excludeUsername) {
        String fromAddress = StringUtils.hasText(appMailFrom) ? appMailFrom : smtpUsername;
        String displayName = (gym != null && StringUtils.hasText(gym.getName())) ? gym.getName() : appMailFromName;
        return new Identity(fromAddress, displayName, resolveReplyTo(gym, excludeUsername));
    }

    /**
     * Immediate, best-effort send (still {@code @Async}, fire-and-forget, under the hood) —
     * reserved for flows where the recipient is actively waiting. Never persisted, never
     * retried: callers that need that already have their own fallback (e.g. the invite link
     * returned in the API response).
     *
     * @param excludeUsername the recipient's own username, so the Reply-To fallback never
     *                        resolves to "reply to yourself" when the recipient is themselves
     *                        the gym's GYM_MANAGER
     */
    public void sendNow(Gym gym, String excludeUsername, String to, String subject, String body) {
        Identity identity = resolveIdentity(gym, excludeUsername);
        mailerService.sendMail(identity.fromAddress(), identity.fromDisplayName(), to, identity.replyTo(), subject, body);
    }

    /**
     * Enqueues a message for {@code EmailOutboxDrainJob} to deliver later. Idempotent on
     * {@code dedupKey} (see {@link EmailOutboxRepository#enqueueIfAbsent}): re-running the same
     * producer on the same day queues nothing new.
     *
     * @return true if a new row was queued, false if {@code dedupKey} already existed
     */
    public boolean enqueue(Gym gym, String to, String subject, String body,
                            EmailCategory category, String dedupKey) {
        if (!StringUtils.hasText(to)) {
            return false;
        }
        Identity identity = resolveIdentity(gym, null);
        LocalDateTime now = LocalDateTime.now();
        int inserted = outboxRepository.enqueueIfAbsent(
                gym != null ? gym.getId() : null,
                identity.fromAddress(), identity.fromDisplayName(), identity.replyTo(),
                to, subject, body, category.name(), dedupKey, now);
        return inserted > 0;
    }

    private String resolveReplyTo(Gym gym, String excludeUsername) {
        if (gym == null) {
            return null;
        }
        if (StringUtils.hasText(gym.getContactEmail())) {
            return gym.getContactEmail();
        }
        List<AppUser> managers = userRepository.findByGymIdAndRole(gym.getId(), "GYM_MANAGER");
        return managers.stream()
                .filter(m -> excludeUsername == null || !m.getUsername().equalsIgnoreCase(excludeUsername))
                .map(AppUser::getUsername)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
