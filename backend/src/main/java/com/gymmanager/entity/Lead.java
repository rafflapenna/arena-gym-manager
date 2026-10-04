package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A prospect who did (or is about to do) a free trial, before becoming a {@link Member}
 * (roadmap #8). Deliberately lightweight — no {@link PersonalInfo} — full personal data
 * (fiscal code, address, certificate) is only collected once, at conversion time, inside
 * {@link com.gymmanager.service.MemberService#create}.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Nullable: for an {@code origin} MEMBER lead the member doesn't know/provide it — the
     * reception fills it in at confirmation or conversion time. Always required for a STAFF
     * lead (enforced by {@code LeadRequestDTO} bean validation, not at the entity level).
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false, length = 20)
    private String phone;

    /** Optional: only required on the {@link Member} the lead eventually converts into. */
    @Column(length = 150)
    private String email;

    /** Free-text provenance (passaparola/social/…), optional. */
    @Column(length = 100)
    private String source;

    /** The discipline/course the prospect is interested in; nullable — resolved the same
     *  way as {@code CourseService.resolveDiscipline} (404 on a cross-gym id). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_course_id")
    private Course interestCourse;

    @Column(name = "trial_date")
    private LocalDate trialDate;

    /**
     * The member who brought this prospect in, if any. Collected at the reception desk while
     * the memory is fresh; carried onto the Member at conversion, which is where it starts
     * mattering for the referral reward.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_member_id")
    private Member referredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status;

    /**
     * Who created this lead — the reception desk, or the member via {@code POST /me/leads}
     * ("prove inserite dai soci", 2026-10-01). Defaulted to STAFF both at the DB level (column
     * default, for rows written before this field existed) and here in {@link #onCreate} (for
     * any code path that builds a {@code Lead} without setting it).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeadOrigin origin;

    /**
     * Logical reference to {@link Member#getId()} (same non-FK reasoning as
     * {@link #convertedMemberId}): the phone on this lead coincides with one already belonging
     * to a member of the gym. Set once, at creation, for a MEMBER-origin lead only; visible
     * only to staff ({@code LeadResponseDTO}), never to the reporting member — a prospect
     * finding out their friend already has an account would be user enumeration.
     */
    @Column(name = "matched_member_id")
    private Long matchedMemberId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Logical reference to {@link Member#getId()} once converted — deliberately NOT a
     * {@code @ManyToOne}, so the lifecycle of the two entities stays decoupled (same
     * reasoning as {@link CheckIn#getTrainingClassId()}). Null until {@code convert} succeeds.
     */
    @Column(name = "converted_member_id")
    private Long convertedMemberId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (origin == null) {
            origin = LeadOrigin.STAFF;
        }
    }
}
