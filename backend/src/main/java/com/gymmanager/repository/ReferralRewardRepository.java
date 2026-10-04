package com.gymmanager.repository;

import com.gymmanager.entity.ReferralReward;
import com.gymmanager.entity.ReferralRewardStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {

    /**
     * Whether the person brought in has already earned their referrer a reward. Guards the
     * "one reward per referred member" rule in the service so a renewal doesn't mint a second
     * discount; the unique constraint on the column is the backstop under a race.
     */
    boolean existsByReferredMemberId(Long referredMemberId);

    /**
     * Rewards belonging to a member, newest first. {@code @EntityGraph} pulls the referred
     * member (and their personal info) in the same round trip because the DTO shows "earned by
     * bringing in <name>" for every row — otherwise it is an N+1 at ~20ms a hop.
     */
    @EntityGraph(attributePaths = {"referredMember", "referredMember.personalInfo"})
    List<ReferralReward> findByBeneficiaryIdOrderByEarnedAtDesc(Long beneficiaryId);

    @EntityGraph(attributePaths = {"referredMember", "referredMember.personalInfo"})
    List<ReferralReward> findByBeneficiaryIdAndStatusOrderByEarnedAtDesc(
            Long beneficiaryId, ReferralRewardStatus status);

    /**
     * Every reward a member has earned, in any status — used by {@code MeLeadService} to
     * compute "rewardEarned" per reported friend. Deliberately WITHOUT the EntityGraph above:
     * the caller only ever reads {@code referredMember.getId()}, and that id is already known
     * on the lazy proxy without hitting the DB, so joining personalInfo here would be a wasted
     * fetch.
     */
    List<ReferralReward> findByBeneficiaryId(Long beneficiaryId);

    /**
     * Wipes every reward a member is involved in, on either side, so the hard delete of that
     * member isn't blocked by the FK. Both sides have to go: a reward whose referred member is
     * gone can no longer be explained, and one belonging to the deleted member is theirs.
     */
    @Modifying
    @Query("DELETE FROM ReferralReward r WHERE r.beneficiary.id = :memberId OR r.referredMember.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
