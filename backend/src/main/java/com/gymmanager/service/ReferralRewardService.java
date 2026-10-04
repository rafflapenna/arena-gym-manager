package com.gymmanager.service;

import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.ReferralRewardResponseDTO;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.ReferralReward;
import com.gymmanager.entity.ReferralRewardStatus;
import com.gymmanager.entity.Subscription;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.ReferralRewardRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Porta un amico": a member who brings someone in earns a percentage off one subscription.
 *
 * <p>The reward is earned when the <em>referred</em> member takes a subscription, not when
 * their record is created — a trial in this app is a {@code Lead}, never a subscription, so
 * a prospect who tries and walks away never costs the gym a discount.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralRewardService {

    private final ReferralRewardRepository rewardRepository;
    private final MemberRepository memberRepository;
    private final CurrentUser currentUser;

    /** Promotion percentage for newly earned rewards. Snapshotted per reward when granted. */
    @Value("${app.referral.percent:20}")
    private BigDecimal defaultPercent;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Grants the referrer their reward, if this member was brought in by someone and hasn't
     * already earned them one. Called from subscription creation, inside that transaction.
     *
     * <p>Silently does nothing when there is no referrer or a reward already exists: it is a
     * side benefit of selling a subscription, never a reason to fail the sale.
     */
    @Transactional
    public void grantForSubscription(Member referredMember, Subscription subscription) {
        Member referrer = referredMember.getReferredBy();
        if (referrer == null) return;

        // One person brought in = one reward, however many times they later renew.
        if (rewardRepository.existsByReferredMemberId(referredMember.getId())) return;

        ReferralReward reward = rewardRepository.save(ReferralReward.builder()
                .gym(referredMember.getGym())
                .beneficiary(referrer)
                .referredMember(referredMember)
                .percent(defaultPercent)
                .status(ReferralRewardStatus.EARNED)
                .build());

        log.info("Referral reward id={} ({}%) earned by memberId={} for bringing in memberId={} (subscriptionId={})",
                reward.getId(), defaultPercent, referrer.getId(), referredMember.getId(), subscription.getId());
    }

    /**
     * Validates that a reward can be spent on a subscription for {@code member} and returns it.
     * Deliberately separate from {@link #markUsed}: the checks must run (and can reject the
     * whole request) before the subscription is written, but the link back can only be set
     * once the subscription has an id.
     */
    @Transactional(readOnly = true)
    public ReferralReward validateForRedemption(Long rewardId, Member member) {
        if (rewardId == null) return null;

        ReferralReward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new ResourceNotFoundException("ReferralReward", rewardId));

        // Someone else's reward is reported as missing, not forbidden (anti-IDOR), the same
        // way cross-gym reads behave everywhere else.
        if (!reward.getBeneficiary().getId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral reward not found");
        }
        if (reward.getStatus() != ReferralRewardStatus.EARNED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This referral discount has already been used");
        }
        if (reward.getExpiresAt() != null && reward.getExpiresAt().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This referral discount has expired");
        }
        return reward;
    }

    /** Applies a reward's percentage to a price, rounded to cents. */
    public BigDecimal applyDiscount(BigDecimal price, ReferralReward reward) {
        if (reward == null || price == null) return price;
        // (price * (100 - percent)) / 100 rather than price * (1 - percent/100): the latter
        // needs an intermediate division that has no exact decimal form for percentages
        // like 3 and throws ArithmeticException.
        return price.multiply(HUNDRED.subtract(reward.getPercent()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Burns the reward onto the subscription it paid for. */
    @Transactional
    public void markUsed(ReferralReward reward, Subscription subscription) {
        if (reward == null) return;
        reward.setStatus(ReferralRewardStatus.USED);
        reward.setUsedOnSubscription(subscription);
        reward.setUsedAt(LocalDateTime.now());
        rewardRepository.save(reward);
        log.info("Referral reward id={} spent on subscriptionId={}", reward.getId(), subscription.getId());
    }

    /** Rewards of a member — all of them, or only those in a given state. */
    @Transactional(readOnly = true)
    public List<ReferralRewardResponseDTO> findByMember(Long memberId, String status) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        assertSameGym(member);

        List<ReferralReward> rewards = (status == null || status.isBlank())
                ? rewardRepository.findByBeneficiaryIdOrderByEarnedAtDesc(memberId)
                : rewardRepository.findByBeneficiaryIdAndStatusOrderByEarnedAtDesc(memberId, parseStatus(status));

        return rewards.stream().map(this::toDTO).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Parsed here rather than bound as an enum @RequestParam, which would 500 on a bad value. */
    private static ReferralRewardStatus parseStatus(String status) {
        try {
            return ReferralRewardStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reward status: " + status);
        }
    }

    private void assertSameGym(Member member) {
        if (currentUser.isAdmin()) return;
        Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
        if (memberGymId == null || !memberGymId.equals(currentUser.gymId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    private ReferralRewardResponseDTO toDTO(ReferralReward r) {
        Member referred = r.getReferredMember();
        return ReferralRewardResponseDTO.builder()
                .id(r.getId())
                .referredMember(NamedRefDTO.builder()
                        .id(referred.getId())
                        .name(MemberService.fullName(referred))
                        .build())
                .percent(r.getPercent())
                .status(r.getStatus().name())
                .expiresAt(r.getExpiresAt())
                .usedOnSubscriptionId(r.getUsedOnSubscription() != null ? r.getUsedOnSubscription().getId() : null)
                .earnedAt(r.getEarnedAt())
                .usedAt(r.getUsedAt())
                .build();
    }
}
