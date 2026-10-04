package com.gymmanager.service;

import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.EmailOutboxStatus;
import com.gymmanager.repository.EmailOutboxRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the {@code email_outbox}: claims a batch of due {@link EmailOutboxStatus#PENDING}
 * rows, sends each over SMTP, then records the outcome — retrying transient failures with
 * backoff and giving up after {@code app.mail.outbox.max-attempts}.
 *
 * <p>Three phases, as prescribed by the design doc, so a transaction is never held open across
 * an SMTP round-trip (which can take seconds and must never hold DB locks that long). The
 * transactional phases live on {@link EmailOutboxBatchService} (not here — this method must
 * stay non-transactional, since it sleeps between sends):
 * <ol>
 *   <li>{@link EmailOutboxBatchService#claimBatch} — one transaction: lock due rows
 *       ({@code FOR UPDATE SKIP LOCKED}, see {@link EmailOutboxRepository#findClaimableIds}),
 *       flip them to SENDING, read them back. Committing releases the locks; the rows are now
 *       "ours" regardless.</li>
 *   <li>{@link #sendOne} — outside any transaction, one row at a time, {@code send-delay-ms}
 *       apart (a plain courtesy to the SMTP server, not a rate-limit mechanism).</li>
 *   <li>{@link EmailOutboxBatchService#applyOutcomes} — one transaction: re-fetch the same rows
 *       and write SENT / back-to-PENDING-with-backoff / FAILED.</li>
 * </ol>
 *
 * <p>From/Reply-To/body are read straight off the row (set by {@code GymMailService#enqueue}
 * at queue time) — never re-resolved from {@code Gym} here, so a detached row sent outside a
 * transaction never risks a {@code LazyInitializationException}.
 *
 * <p>{@code app.mail.outbox.daily-cap} caps how many rows get claimed per run against today's
 * {@link EmailOutboxStatus#SENT} count (Brevo's free plan is a shared, account-wide quota) —
 * checked before claiming, not after, so a run never claims more than the remaining budget.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxDrainJob {

    private final EmailOutboxRepository outboxRepository;
    private final EmailOutboxBatchService batchService;
    private final JavaMailSender mailSender;

    @Value("${app.mail.outbox.batch-size:20}")
    private int batchSize;

    @Value("${app.mail.outbox.send-delay-ms:200}")
    private long sendDelayMs;

    @Value("${app.mail.outbox.daily-cap:280}")
    private int dailyCap;

    @Value("${app.mail.outbox.stale-sending-minutes:10}")
    private int staleSendingMinutes;

    @Value("${app.mail.outbox.max-attempts:5}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.mail.outbox.drain-fixed-delay-ms:60000}", zone = "Europe/Rome")
    public void drain() {
        LocalDateTime now = LocalDateTime.now();
        batchService.recoverStaleSending(now, staleSendingMinutes);

        long remainingCap = dailyCap - outboxRepository.countByStatusAndSentAtBetween(
                EmailOutboxStatus.SENT, startOfDay(now), startOfDay(now).plusDays(1));
        if (remainingCap <= 0) {
            log.debug("EmailOutboxDrainJob: daily cap reached ({}), skipping run", dailyCap);
            return;
        }

        List<EmailOutbox> claimed = batchService.claimBatch(now, (int) Math.min(batchSize, remainingCap));
        if (claimed.isEmpty()) {
            return;
        }

        Map<Long, EmailSendOutcome> outcomes = new LinkedHashMap<>();
        for (EmailOutbox row : claimed) {
            outcomes.put(row.getId(), sendOne(row));
            sleep(sendDelayMs);
        }
        Map<EmailOutboxStatus, Long> counts = batchService.applyOutcomes(outcomes, maxAttempts);

        log.info("EmailOutboxDrainJob: processed {} email(s) — {} sent, {} failed, {} retrying",
                claimed.size(), counts.getOrDefault(EmailOutboxStatus.SENT, 0L),
                counts.getOrDefault(EmailOutboxStatus.FAILED, 0L),
                counts.getOrDefault(EmailOutboxStatus.PENDING, 0L));
    }

    /** No transaction: this is the SMTP round-trip. */
    private EmailSendOutcome sendOne(EmailOutbox row) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(row.getFromAddress(), row.getFromDisplayName(), "UTF-8"));
            helper.setTo(row.getToAddress());
            if (StringUtils.hasText(row.getReplyTo())) {
                helper.setReplyTo(row.getReplyTo());
            }
            helper.setSubject(row.getSubject());
            helper.setText(row.getBody());

            mailSender.send(message);
            return EmailSendOutcome.sent();
        } catch (AddressException e) {
            // Malformed address: retrying would never help.
            return EmailSendOutcome.permanentFailure(e.getMessage());
        } catch (Exception e) {
            return EmailSendOutcome.transientFailure(e.getMessage());
        }
    }

    private static LocalDateTime startOfDay(LocalDateTime now) {
        return now.toLocalDate().atStartOfDay();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
