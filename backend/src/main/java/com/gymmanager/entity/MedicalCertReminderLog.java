package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Idempotency log for {@link com.gymmanager.service.MedicalCertReminderJob}: one row per
 * (document, threshold) pair already emailed, so a nightly re-run never re-sends the same
 * reminder for the same certificate.
 */
@Entity
@Table(name = "medical_cert_reminder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalCertReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private MemberDocument document;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** Which threshold (e.g. 30 or 7 days before expiry) this reminder was sent for. */
    @Column(name = "days_before", nullable = false)
    private Integer daysBefore;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
