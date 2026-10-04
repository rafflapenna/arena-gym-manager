package com.gymmanager.service;

import com.gymmanager.entity.EmailCategory;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nightly job that queues a renewal reminder for members whose subscription is about to
 * expire, at the single strictest configured threshold that applies (default: 7 and 1 days
 * before {@code endDate}). Runs after {@link SubscriptionExpiryJob} (01:00) so it never nags
 * about one it just expired, and before {@link MedicalCertReminderJob} (01:30).
 *
 * <p>Reuses {@link SubscriptionRepository#findExpiringSoon} — the same query that backs
 * {@code GET /subscriptions/expiring} — with {@code gymId = null} (every gym) and the widest
 * configured threshold as the window. A subscription is skipped when the member already has a
 * later one starting after this one's {@code endDate}
 * ({@link SubscriptionRepository#existsByMemberIdAndStartDateAfter}): they've already renewed,
 * so reminding them would be noise, not a service.
 *
 * <p><b>One reminder per night, not one per threshold:</b> a single query over [today,
 * maxThreshold] would otherwise match a subscription expiring in 3 days against both the "7"
 * and (once it gets closer) "1" thresholds — two emails the same night. Instead, for each
 * candidate only the <em>smallest</em> configured threshold still {@code >=} its actual
 * {@code daysUntilExpiry} is used, so a 3-day subscription gets exactly one "7" reminder (then,
 * a few nights later, exactly one "1" reminder — different {@code dedup_key}, different night).
 *
 * <p>Idempotent the same way as every other outbox producer: {@code dedup_key} =
 * {@code subrenew:{subscriptionId}:{daysBefore}}, enforced by the outbox's unique index, so a
 * re-run (or the query naturally re-matching the same subscription on subsequent days within
 * the same threshold window) never queues the same reminder twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalReminderJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusRepository subscriptionStatusRepository;
    private final GymMailService gymMailService;

    @Value("${app.subscription.renewal.enabled:true}")
    private boolean enabled;

    @Value("${app.subscription.renewal.days:7,1}")
    private String reminderDaysConfig;

    @Scheduled(cron = "${app.subscription.renewal.cron:0 15 1 * * *}", zone = "Europe/Rome")
    @Transactional
    public void sendReminders() {
        if (!enabled) {
            return;
        }
        SubscriptionStatus activeStatus = subscriptionStatusRepository.findByCode("ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Status ACTIVE not found"));

        List<Integer> thresholds = parseReminderDays();
        if (thresholds.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        int maxThreshold = thresholds.stream().max(Integer::compareTo).orElseThrow();
        List<Subscription> candidates = subscriptionRepository.findExpiringSoon(
                today, today.plusDays(maxThreshold), activeStatus.getId(), null);

        int queued = 0;
        for (Subscription subscription : candidates) {
            long daysUntilExpiry = ChronoUnit.DAYS.between(today, subscription.getEndDate());
            Optional<Integer> threshold = strictestThreshold(thresholds, daysUntilExpiry);
            if (threshold.isPresent() && queueReminder(subscription, threshold.get(), daysUntilExpiry)) {
                queued++;
            }
        }
        if (queued > 0) {
            log.info("SubscriptionRenewalReminderJob: queued {} reminder(s)", queued);
        }
    }

    /** The smallest configured threshold still {@code >= daysUntilExpiry} — see class javadoc. */
    private Optional<Integer> strictestThreshold(List<Integer> thresholds, long daysUntilExpiry) {
        return thresholds.stream()
                .filter(t -> daysUntilExpiry <= t)
                .min(Comparator.naturalOrder());
    }

    private boolean queueReminder(Subscription subscription, int daysBefore, long daysUntilExpiry) {
        Member member = subscription.getMember();
        if (!Boolean.TRUE.equals(member.getActive())) {
            return false;
        }
        // Already renewed: a later subscription already starts after this one ends.
        if (subscriptionRepository.existsByMemberIdAndStartDateAfter(member.getId(), subscription.getEndDate())) {
            return false;
        }
        PersonalInfo personalInfo = member.getPersonalInfo();
        String to = personalInfo.getEmail();
        if (!StringUtils.hasText(to)) {
            return false;
        }

        Gym gym = member.getGym();
        String displayName = (gym != null && StringUtils.hasText(gym.getName())) ? gym.getName() : "Gym Manager";
        String label = subscriptionLabel(subscription);

        String body = """
                Ciao %s,

                Il tuo abbonamento%s scade il %s (tra %d giorni).
                Passa in palestra per rinnovarlo e continuare ad allenarti senza interruzioni.

                %s
                """.formatted(personalInfo.getFirstName(), label, subscription.getEndDate(), daysUntilExpiry, displayName);

        return gymMailService.enqueue(gym, to, "Il tuo abbonamento sta per scadere", body,
                EmailCategory.REMINDER, "subrenew:%d:%d".formatted(subscription.getId(), daysBefore));
    }

    /** " \"Mensile\"" style suffix, or "" if neither a type nor a package is set. */
    private String subscriptionLabel(Subscription subscription) {
        if (subscription.getType() != null && StringUtils.hasText(subscription.getType().getLabel())) {
            return " \"%s\"".formatted(subscription.getType().getLabel());
        }
        if (subscription.getTrainingPackage() != null && StringUtils.hasText(subscription.getTrainingPackage().getName())) {
            return " \"%s\"".formatted(subscription.getTrainingPackage().getName());
        }
        return "";
    }

    private List<Integer> parseReminderDays() {
        return Arrays.stream(reminderDaysConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Integer::parseInt)
                .toList();
    }
}
