package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** Gym (tenant) this check-in belongs to; denormalized from the member for fast gym-scoped queries. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInSource source;

    /**
     * Course (Course.id) this check-in is attributed to, set only when the trainer marks
     * attendance on a specific course; {@code null} for plain reception/self check-ins,
     * which have no course context. Kept as a plain nullable column rather than a real
     * {@code @ManyToOne} so introducing a dedicated {@code TrainingClass} entity later
     * (course occurrences vs. the recurring course definition) is a non-destructive change.
     */
    @Column(name = "training_class_id")
    private Long trainingClassId;

    /** AppUser id that registered the check-in (the operator, or the member for self check-in). */
    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (checkInAt == null) {
            checkInAt = LocalDateTime.now();
        }
    }
}
