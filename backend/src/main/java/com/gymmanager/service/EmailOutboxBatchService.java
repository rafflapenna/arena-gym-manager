package com.gymmanager.service;

import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.EmailOutboxStatus;
import com.gymmanager.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The three transactional phases of {@link EmailOutboxDrainJob}, split into their own bean —
 * same reason {@link MailerService} is split out of {@link InviteService}: {@code @Transactional}
 * only takes effect through Spring's proxy, and a method calling another method on {@code this}
 * bypasses that proxy entirely (self-invocation). {@link EmailOutboxDrainJob#drain} must NOT
 * itself be transactional (it sleeps between sends, and must never hold a DB transaction open
 * across an SMTP round-trip), so the actual transactions have to live on a different bean.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxBatchService {

    /** attempts=1 → 1m, attempts=2 → 5m, attempts=3 → 30m, attempts=4 → 2h; a 5th failure
     *  (attempts=5, the default {@code max-attempts}) gives up instead of scheduling a 6th. */
    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(12)
    };

    private final EmailOutboxRepository outboxRepository;

    /** A row stuck in SENDING for longer than {@code staleMinutes} means the process crashed
     *  mid-send; hand it back to the queue (rare double-send risk on resume, accepted). */
    @Transactional
    public void recoverStaleSending(LocalDateTime now, int staleMinutes) {
        LocalDateTime cutoff = now.minusMinutes(staleMinutes);
        int recovered = outboxRepository.recoverStaleSending(
                EmailOutboxStatus.PENDING, EmailOutboxStatus.SENDING, cutoff, now);
        if (recovered > 0) {
            log.warn("EmailOutboxDrainJob: recovered {} row(s) stuck in SENDING (crash recovery)", recovered);
        }
    }

    /**
     * Locks up to {@code limit} due rows ({@code FOR UPDATE SKIP LOCKED}), flips them to
     * SENDING and reads them back — all in one transaction, so the lock is held across both
     * statements and released only once they're already marked as ours.
     */
    @Transactional
    public List<EmailOutbox> claimBatch(LocalDateTime now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Long> ids = outboxRepository.findClaimableIds(now, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        outboxRepository.markSending(ids, EmailOutboxStatus.SENDING, now);
        return outboxRepository.findAllById(ids);
    }

    /** Writes SENT / back-to-PENDING-with-backoff / FAILED for a claimed batch, in one
     *  transaction. Returns the resulting status counts, for the drain job's log line. */
    @Transactional
    public Map<EmailOutboxStatus, Long> applyOutcomes(Map<Long, EmailSendOutcome> outcomes, int maxAttempts) {
        LocalDateTime now = LocalDateTime.now();
        List<EmailOutbox> rows = outboxRepository.findAllById(outcomes.keySet());
        for (EmailOutbox row : rows) {
            EmailSendOutcome outcome = outcomes.get(row.getId());
            row.setUpdatedAt(now);
            switch (outcome.kind()) {
                case SENT -> {
                    row.setStatus(EmailOutboxStatus.SENT);
                    row.setSentAt(now);
                    row.setLastError(null);
                }
                case PERMANENT_FAILURE -> {
                    row.setStatus(EmailOutboxStatus.FAILED);
                    row.setAttempts(row.getAttempts() + 1);
                    row.setLastError(outcome.error());
                }
                case TRANSIENT_FAILURE -> {
                    int attempts = row.getAttempts() + 1;
                    row.setAttempts(attempts);
                    row.setLastError(outcome.error());
                    if (attempts >= maxAttempts) {
                        row.setStatus(EmailOutboxStatus.FAILED);
                    } else {
                        row.setStatus(EmailOutboxStatus.PENDING);
                        row.setNextAttemptAt(now.plus(BACKOFF[Math.min(attempts, BACKOFF.length) - 1]));
                    }
                }
            }
        }
        outboxRepository.saveAll(rows);
        return rows.stream().collect(Collectors.groupingBy(EmailOutbox::getStatus, Collectors.counting()));
    }
}
