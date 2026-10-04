package com.gymmanager.repository;

import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.EmailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Idempotent enqueue: native {@code INSERT ... ON CONFLICT (dedup_key) DO NOTHING}.
     * A plain JPA {@code save()} + catching {@code DataIntegrityViolationException} would also
     * work in isolation, but Postgres aborts the *whole* transaction on the first constraint
     * violation regardless of whether the Java exception is caught — so every producer would
     * need its own {@code REQUIRES_NEW} just for this insert. {@code ON CONFLICT DO NOTHING}
     * sidesteps that: no error is ever raised, so this can run inside the producer's own
     * {@code @Transactional} method. Returns the number of rows actually inserted (0 or 1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO email_outbox
                (gym_id, from_address, from_display_name, reply_to, to_address, subject, body,
                 category, status, attempts, next_attempt_at, scheduled_at, dedup_key, created_at)
            VALUES
                (:gymId, :fromAddress, :fromDisplayName, :replyTo, :toAddress, :subject, :body,
                 :category, 'PENDING', 0, :now, :now, :dedupKey, :now)
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(@Param("gymId") Long gymId,
                         @Param("fromAddress") String fromAddress,
                         @Param("fromDisplayName") String fromDisplayName,
                         @Param("replyTo") String replyTo,
                         @Param("toAddress") String toAddress,
                         @Param("subject") String subject,
                         @Param("body") String body,
                         @Param("category") String category,
                         @Param("dedupKey") String dedupKey,
                         @Param("now") LocalDateTime now);

    /**
     * Ids of up to {@code limit} PENDING rows due now, locked {@code FOR UPDATE SKIP LOCKED} so
     * a second instance (or an overlapping run) never grabs the same row — see
     * {@code EmailOutboxDrainJob}'s "claim" phase. Ordered by category priority
     * (TRANSACTIONAL > REMINDER > MARKETING, per the shared Brevo daily cap) then by id (FIFO
     * within a category).
     */
    @Query(value = """
            SELECT id FROM email_outbox
            WHERE status = 'PENDING'
              AND scheduled_at <= :now
              AND next_attempt_at <= :now
            ORDER BY
                CASE category WHEN 'TRANSACTIONAL' THEN 0 WHEN 'REMINDER' THEN 1 ELSE 2 END,
                id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Flips claimed ids to SENDING in the same transaction that locked them. */
    @Modifying
    @Query("UPDATE EmailOutbox e SET e.status = :sending, e.updatedAt = :now WHERE e.id IN :ids")
    int markSending(@Param("ids") List<Long> ids,
                     @Param("sending") EmailOutboxStatus sending,
                     @Param("now") LocalDateTime now);

    /** A row stuck in SENDING (process crashed mid-send) is handed back to the queue. Rare
     *  double-send risk on resume, accepted per the design doc. */
    @Modifying
    @Query("UPDATE EmailOutbox e SET e.status = :pending, e.updatedAt = :now " +
           "WHERE e.status = :sending AND e.updatedAt < :cutoff")
    int recoverStaleSending(@Param("pending") EmailOutboxStatus pending,
                             @Param("sending") EmailOutboxStatus sending,
                             @Param("cutoff") LocalDateTime cutoff,
                             @Param("now") LocalDateTime now);

    /** Today's send count against {@code app.mail.outbox.daily-cap} — SENT rows only, so a
     *  FAILED/retrying row never counts against the budget. */
    long countByStatusAndSentAtBetween(EmailOutboxStatus status, LocalDateTime from, LocalDateTime to);
}
