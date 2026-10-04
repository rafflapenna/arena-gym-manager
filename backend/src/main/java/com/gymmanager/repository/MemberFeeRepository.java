package com.gymmanager.repository;

import com.gymmanager.entity.MemberFee;
import com.gymmanager.entity.MemberFeeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MemberFeeRepository extends JpaRepository<MemberFee, Long> {

    /** A member's fees, newest first; federation + card type joined for the DTO. */
    @EntityGraph(attributePaths = {"federation", "cardType", "paymentMethod"})
    List<MemberFee> findByMemberIdOrderByValidUntilDesc(Long memberId);

    /**
     * Fees of a gym expiring within a window, for the renewal reminders — same shape as
     * {@code /subscriptions/expiring}, and filtered DB-side rather than in memory because a
     * gym's whole fee history grows without bound.
     */
    @EntityGraph(attributePaths = {"member", "member.personalInfo", "federation", "cardType"})
    @Query("""
            SELECT f FROM MemberFee f
            WHERE f.member.gym.id = :gymId
              AND f.validUntil BETWEEN :from AND :to
            ORDER BY f.validUntil ASC
            """)
    List<MemberFee> findExpiring(@Param("gymId") Long gymId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /**
     * True when the member already has a later fee of the same kind — same {@code type}, and
     * for FEDERATION also the same {@code federation} (not necessarily the same card type: a
     * renewal may move the member to a different age/level line within that federation) —
     * i.e. they've already renewed, so a reminder for the one expiring on {@code validUntil}
     * would be noise. {@code federationId = null} for ASSOCIATION, which has no federation.
     */
    @Query("""
            SELECT COUNT(f) > 0 FROM MemberFee f
            WHERE f.member.id = :memberId
              AND f.type = :type
              AND (:federationId IS NULL OR f.federation.id = :federationId)
              AND f.validUntil > :validUntil
            """)
    boolean existsByMemberIdAndTypeAndFederationAndValidUntilAfter(
            @Param("memberId") Long memberId,
            @Param("type") MemberFeeType type,
            @Param("federationId") Long federationId,
            @Param("validUntil") LocalDate validUntil);

    /** Guards deleting a fee-list line that has already been charged to someone. */
    boolean existsByCardTypeId(Long cardTypeId);

    @Modifying
    @Query("DELETE FROM MemberFee f WHERE f.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
