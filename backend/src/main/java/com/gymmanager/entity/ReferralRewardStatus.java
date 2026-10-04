package com.gymmanager.entity;

/**
 * Lifecycle of a {@link ReferralReward} — the discount a member earns for bringing someone
 * who then signs up ("porta un amico").
 */
public enum ReferralRewardStatus {
    /** Earned and still spendable on a future subscription. */
    EARNED,
    /** Already applied to a subscription ({@link ReferralReward#getUsedOnSubscription()}). */
    USED,
    /** Past its expiry date without being used; kept for history, never spendable again. */
    EXPIRED
}
