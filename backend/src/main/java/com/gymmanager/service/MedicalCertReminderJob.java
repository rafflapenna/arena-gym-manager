package com.gymmanager.service;

import com.gymmanager.entity.EmailCategory;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MedicalCertReminderLog;
import com.gymmanager.entity.MemberDocument;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.MedicalCertReminderLogRepository;
import com.gymmanager.repository.MemberDocumentRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Nightly job that queues a reminder for members whose {@code MEDICAL_CERT} is about to
 * expire, at the single strictest configured threshold that applies (default: 30 and 7 days
 * before {@code expiryDate}).
 *
 * <p>Converted to queue-only (roadmap: scheduler/outbox, phase 1) — it used to send the email
 * inline via SMTP, which meant an SMTP outage silently dropped the reminder (the dedup log was
 * written regardless of send outcome, so there was no retry). It now only enqueues via
 * {@link GymMailService#enqueue}, whose {@code dedup_key} is the idempotency mechanism going
 * forward; {@link MedicalCertReminderLogRepository} is kept only as a (document, daysBefore)
 * pre-check so a re-run doesn't even attempt the enqueue call, and because
 * {@code MemberService}'s delete path already cleans it up — not because it's still load-bearing
 * for correctness (the outbox's unique {@code dedup_key} would catch a duplicate on its own).
 * {@code EmailOutboxDrainJob} does the actual SMTP send, with retry/backoff.
 *
 * <p>Runs a single query over [today, maxThreshold] rather than one per threshold, and picks
 * only the smallest configured threshold still {@code >=} the document's actual
 * {@code daysUntilExpiry} (same reasoning as {@link SubscriptionRenewalReminderJob}): otherwise
 * a certificate expiring in, say, 5 days would match both the "30" and "7" thresholds in the
 * same run and queue two reminders the same night. The {@code dedup_key} format and the
 * per-(document, daysBefore) log are unchanged.
 *
 * <p>Only the most recent certificate (max {@code expiryDate}) per member is considered —
 * a superseded/expired past upload must not trigger a reminder.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicalCertReminderJob {

    private final MemberDocumentRepository memberDocumentRepository;
    private final MedicalCertReminderLogRepository reminderLogRepository;
    private final GymMailService gymMailService;

    @Value("${app.document.medical-cert.reminder-days:30,7}")
    private String reminderDaysConfig;

    @Scheduled(cron = "${app.document.medical-cert.reminder-cron:0 30 1 * * *}", zone = "Europe/Rome")
    @Transactional
    public void sendReminders() {
        List<Integer> thresholds = parseReminderDays();
        if (thresholds.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        int maxThreshold = thresholds.stream().max(Integer::compareTo).orElseThrow();
        List<MemberDocument> candidates =
                memberDocumentRepository.findExpiringMedicalCerts(today, today.plusDays(maxThreshold), null);

        for (MemberDocument doc : mostRecentPerMember(candidates)) {
            long daysUntilExpiry = ChronoUnit.DAYS.between(today, doc.getExpiryDate());
            strictestThreshold(thresholds, daysUntilExpiry)
                    .ifPresent(daysBefore -> maybeSendReminder(doc, daysBefore, daysUntilExpiry));
        }
    }

    /** The smallest configured threshold still {@code >= daysUntilExpiry} — see class javadoc. */
    private Optional<Integer> strictestThreshold(List<Integer> thresholds, long daysUntilExpiry) {
        return thresholds.stream()
                .filter(t -> daysUntilExpiry <= t)
                .min(Comparator.naturalOrder());
    }

    /** Keeps only the certificate with the max expiryDate per member (a member may have several). */
    private List<MemberDocument> mostRecentPerMember(List<MemberDocument> documents) {
        Map<Long, MemberDocument> byMember = documents.stream()
                .collect(Collectors.toMap(
                        d -> d.getMember().getId(),
                        d -> d,
                        (a, b) -> a.getExpiryDate().isAfter(b.getExpiryDate()) ? a : b
                ));
        return List.copyOf(byMember.values());
    }

    private void maybeSendReminder(MemberDocument document, int daysBefore, long daysUntilExpiry) {
        if (reminderLogRepository.existsByDocumentIdAndDaysBefore(document.getId(), daysBefore)) {
            return;
        }
        queueReminderEmail(document, daysBefore, daysUntilExpiry);

        reminderLogRepository.save(MedicalCertReminderLog.builder()
                .document(document)
                .daysBefore(daysBefore)
                .build());
        log.info("Queued medical cert reminder documentId={} memberId={} daysBefore={}",
                document.getId(), document.getMember().getId(), daysBefore);
    }

    private void queueReminderEmail(MemberDocument document, int daysBefore, long daysUntilExpiry) {
        Member member = document.getMember();
        PersonalInfo personalInfo = member.getPersonalInfo();
        String to = personalInfo.getEmail();
        if (!StringUtils.hasText(to)) {
            return;
        }
        Gym gym = member.getGym();
        String displayName = (gym != null && StringUtils.hasText(gym.getName())) ? gym.getName() : "Gym Manager";
        String body = """
                Ciao %s,

                Il tuo certificato medico scade il %s (tra %d giorni).
                Ricordati di rinnovarlo per continuare ad allenarti senza interruzioni.

                %s
                """.formatted(personalInfo.getFirstName(), document.getExpiryDate(), daysUntilExpiry, displayName);

        gymMailService.enqueue(gym, to, "Il tuo certificato medico sta per scadere", body,
                EmailCategory.REMINDER, "cert:%d:%d".formatted(document.getId(), daysBefore));
    }

    private List<Integer> parseReminderDays() {
        return Arrays.stream(reminderDaysConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Integer::parseInt)
                .toList();
    }
}
