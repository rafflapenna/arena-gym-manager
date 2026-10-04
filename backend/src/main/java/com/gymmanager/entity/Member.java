package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "personal_info_id", nullable = false)
    private PersonalInfo personalInfo;

    /** Gym (tenant) this member belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id")
    private Gym gym;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * The member who brought this one in ("porta un amico"), or null. A plain reference, not
     * a lifecycle dependency: deleting the referrer must not cascade onto this member, so it
     * stays optional and unenforced beyond the FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_member_id")
    private Member referredBy;

    /**
     * Opaque random token (256-bit, base64url) backing the member's personal QR identity
     * card used for check-in (roadmap #5). Not a signed JWT: short, printable, and
     * revocable with a plain UPDATE ({@link com.gymmanager.service.CheckinTokenService#rotate}).
     * Nullable: generated lazily on first read, which backfills existing members without
     * a data migration script.
     */
    @Column(name = "checkin_token", unique = true, length = 64)
    private String checkinToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
