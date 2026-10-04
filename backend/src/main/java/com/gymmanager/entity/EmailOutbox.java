package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One queued email. Producers ({@code SubscriptionRenewalReminderJob}, {@code FeeReminderJob},
 * {@code MedicalCertReminderJob}) only insert rows (via {@code GymMailService#enqueue}, idempotent
 * on {@link #dedupKey}); {@code EmailOutboxDrainJob} is the only writer of everything else.
 *
 * <p>From/Reply-To are resolved and snapshotted at enqueue time (not re-resolved at send time):
 * the drainer sends outside any transaction and must never touch a lazy {@code Gym} association
 * from a detached row, and freezing the identity also means a gym renaming itself mid-queue
 * doesn't retroactively change an email already waiting to go out.
 *
 * <p>{@link #dedupKey} is unique (see {@code uk_email_outbox_dedup_key} below and the matching
 * index in {@code schema.sql}) — declared in both places so it exists from the very first boot
 * even in tests, which create the schema from this mapping rather than running {@code schema.sql}
 * against an already-populated table.
 */
@Entity
@Table(name = "email_outbox", uniqueConstraints = @UniqueConstraint(columnNames = "dedup_key", name = "uk_email_outbox_dedup_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable: a future ADMIN-originated email (none today) wouldn't have one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id")
    private Gym gym;

    @Column(name = "from_address", nullable = false, length = 150)
    private String fromAddress;

    @Column(name = "from_display_name", nullable = false, length = 150)
    private String fromDisplayName;

    @Column(name = "reply_to", length = 150)
    private String replyTo;

    @Column(name = "to_address", nullable = false, length = 150)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    /** When this row becomes claimable; also where backoff pushes retries out to. */
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    /** When the producer wanted it sent — equals creation time for every MVP producer
     *  (nothing schedules a future send yet; reserved for the phase-2 broadcast). */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /** e.g. {@code subrenew:{subId}:7}, {@code fee:{feeId}:30}, {@code cert:{docId}:30}. */
    @Column(name = "dedup_key", nullable = false, length = 150)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Bumped explicitly by the drainer's bulk updates (claim/outcome), which bypass
     *  {@code @PreUpdate} — used to detect a row stuck in SENDING after a crash. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        if (scheduledAt == null) {
            scheduledAt = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = scheduledAt;
        }
    }
}
