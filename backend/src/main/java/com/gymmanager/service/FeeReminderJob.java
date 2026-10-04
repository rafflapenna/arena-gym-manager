package com.gymmanager.service;

import com.gymmanager.entity.EmailCategory;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MemberFee;
import com.gymmanager.entity.MemberFeeType;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberFeeRepository;
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
 * Nightly job that queues a renewal reminder for members whose {@link MemberFee} — quota
 * associativa or federation card — is about to expire, at the single strictest configured
 * threshold that applies (default: 30 and 7 days before {@code validUntil}). Runs after
 * {@link MedicalCertReminderJob} (01:30).
 *
 * <p>{@link MemberFeeRepository#findExpiring} is gym-scoped (no {@code gymId = null} / "all
 * gyms" option, unlike the subscription queries), so this iterates every {@link Gym} — fine at
 * today's scale, and consistent with {@code MemberFeeService.findExpiring} being called per
 * gym from the authenticated request too.
 *
 * <p>Like {@link SubscriptionRenewalReminderJob}: only the smallest configured threshold still
 * {@code >=} the fee's actual {@code daysUntilExpiry} is used (see that class's javadoc for why
 * — otherwise e.g. a fee expiring in 20 days would match both "30" and, later, "7", or worse,
 * a single run spanning both windows would fire twice the same night), and a fee is skipped
 * when the member already has a later fee of the same kind
 * ({@link MemberFeeRepository#existsByMemberIdAndTypeAndFederationAndValidUntilAfter}) — they've
 * already renewed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeeReminderJob {

    private final GymRepository gymRepository;
    private final MemberFeeRepository memberFeeRepository;
    private final GymMailService gymMailService;

    @Value("${app.fee.reminder-enabled:true}")
    private boolean enabled;

    @Value("${app.fee.reminder-days:30,7}")
    private String reminderDaysConfig;

    @Scheduled(cron = "${app.fee.reminder-cron:0 45 1 * * *}", zone = "Europe/Rome")
    @Transactional
    public void sendReminders() {
        if (!enabled) {
            return;
        }
        List<Integer> thresholds = parseReminderDays();
        if (thresholds.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        int maxThreshold = thresholds.stream().max(Integer::compareTo).orElseThrow();
        List<Gym> gyms = gymRepository.findAll();

        int queued = 0;
        for (Gym gym : gyms) {
            for (MemberFee fee : memberFeeRepository.findExpiring(gym.getId(), today, today.plusDays(maxThreshold))) {
                long daysUntilExpiry = ChronoUnit.DAYS.between(today, fee.getValidUntil());
                Optional<Integer> threshold = strictestThreshold(thresholds, daysUntilExpiry);
                if (threshold.isPresent() && queueReminder(gym, fee, threshold.get(), daysUntilExpiry)) {
                    queued++;
                }
            }
        }
        if (queued > 0) {
            log.info("FeeReminderJob: queued {} reminder(s)", queued);
        }
    }

    /** The smallest configured threshold still {@code >= daysUntilExpiry} — see class javadoc. */
    private Optional<Integer> strictestThreshold(List<Integer> thresholds, long daysUntilExpiry) {
        return thresholds.stream()
                .filter(t -> daysUntilExpiry <= t)
                .min(Comparator.naturalOrder());
    }

    private boolean queueReminder(Gym gym, MemberFee fee, int daysBefore, long daysUntilExpiry) {
        Member member = fee.getMember();
        if (!Boolean.TRUE.equals(member.getActive())) {
            return false;
        }
        // Already renewed: a later fee of the same kind (same type, same federation if any)
        // already covers the period after this one expires.
        Long federationId = fee.getFederation() != null ? fee.getFederation().getId() : null;
        if (memberFeeRepository.existsByMemberIdAndTypeAndFederationAndValidUntilAfter(
                member.getId(), fee.getType(), federationId, fee.getValidUntil())) {
            return false;
        }
        PersonalInfo personalInfo = member.getPersonalInfo();
        String to = personalInfo.getEmail();
        if (!StringUtils.hasText(to)) {
            return false;
        }

        String displayName = StringUtils.hasText(gym.getName()) ? gym.getName() : "Gym Manager";
        String label = feeLabel(fee);

        String body = """
                Ciao %s,

                %s scade il %s (tra %d giorni).
                Passa in palestra per rinnovarla ed evitare interruzioni.

                %s
                """.formatted(personalInfo.getFirstName(), label, fee.getValidUntil(), daysUntilExpiry, displayName);

        return gymMailService.enqueue(gym, to, "La tua quota/tessera sta per scadere", body,
                EmailCategory.REMINDER, "fee:%d:%d".formatted(fee.getId(), daysBefore));
    }

    private String feeLabel(MemberFee fee) {
        if (fee.getType() == MemberFeeType.FEDERATION && fee.getFederation() != null) {
            return "La tua tessera %s".formatted(fee.getFederation().getName());
        }
        return "La tua quota associativa";
    }

    private List<Integer> parseReminderDays() {
        return Arrays.stream(reminderDaysConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Integer::parseInt)
                .toList();
    }
}
