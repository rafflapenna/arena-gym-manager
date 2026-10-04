package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A discount earned by a member for bringing someone who then signed up ("porta un amico":
 * the referrer gets a percentage off one subscription).
 *
 * <p>The reward is created when the <em>referred</em> member takes their first subscription —
 * not when their record is created — so a prospect who is registered but never actually
 * enrolls doesn't cost the gym a discount. In this app a free trial is a {@code Lead}, never
 * a subscription, so "first subscription" already means "really signed up".
 *
 * <p>{@link #referredMember} is <strong>unique</strong>: one person brought in earns the
 * referrer exactly one reward, no matter how many times that person later renews.
 */
@Entity
@Table(
        name = "referral_rewards",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_referral_rewards_referred_member",
                columnNames = "referred_member_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    /** The member who brought someone in and gets the discount. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_member_id", nullable = false)
    private Member beneficiary;

    /** The member who was brought in, i.e. what earned the reward. Unique (see class doc). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_member_id", nullable = false)
    private Member referredMember;

    /**
     * Discount percentage, snapshotted at the moment the reward is earned rather than read
     * from configuration when it is spent: if the gym later changes the promotion, rewards
     * already promised keep the percentage they were promised at.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralRewardStatus status;

    /** Optional deadline. Null = never expires, which is the default promotion. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** The subscription the discount was applied to; null while still EARNED. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_on_subscription_id")
    private Subscription usedOnSubscription;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        earnedAt = LocalDateTime.now();
    }
}
