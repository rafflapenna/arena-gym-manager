package com.gymmanager.repository;

import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // NOTE: s.type is now nullable (roadmap §4 A.4 — package-based subscriptions have no
    // SubscriptionType), so it's LEFT JOIN FETCHed: an inner JOIN FETCH would silently drop
    // every package-based subscription from these listings. s.trainingPackage is left-joined
    // for the same reason (null for classic type-based subscriptions). trainingPackage.courses
    // is also left-joined so toPackageRefDTO's p.getCourses() never lazy-loads per row (N+1);
    // DISTINCT dedupes the row-per-course fanout that JOIN FETCHing a @ManyToMany produces.
    @Query("""
            SELECT DISTINCT s FROM Subscription s
            JOIN FETCH s.member m JOIN FETCH m.personalInfo
            LEFT JOIN FETCH s.type LEFT JOIN FETCH s.trainingPackage tp LEFT JOIN FETCH tp.courses
            JOIN FETCH s.status
            WHERE s.member.id = :memberId
            ORDER BY s.startDate DESC
            """)
    List<Subscription> findByMemberIdOrderByStartDateDesc(@Param("memberId") Long memberId);

    // Gym-scoped the same way as findExpiringSoon below (pass gymId = null for ADMIN / all gyms).
    @Query("""
            SELECT DISTINCT s FROM Subscription s
            JOIN FETCH s.member m JOIN FETCH m.personalInfo
            LEFT JOIN FETCH s.type LEFT JOIN FETCH s.trainingPackage tp LEFT JOIN FETCH tp.courses
            JOIN FETCH s.status st
            WHERE st = :status
              AND (:gymId IS NULL OR s.member.gym.id = :gymId)
            ORDER BY s.endDate ASC
            """)
    List<Subscription> findByStatusOrderByEndDateAsc(
            @Param("status") SubscriptionStatus status, @Param("gymId") Long gymId);

    /**
     * Subscriptions that are still ACTIVE and expire within [today, threshold],
     * optionally scoped to a single gym (pass {@code gymId = null} for ADMIN / all gyms).
     *
     * <p>Uses {@code status.id} instead of {@code status.code} so the query hits
     * {@code ix_subscriptions_status_id_end_date} directly without a string
     * comparison on an un-indexed column. The active status id is resolved once
     * from the cached lookup list — no extra DB round-trip per call.
     */
    @Query("""
            SELECT DISTINCT s FROM Subscription s
            JOIN FETCH s.member m JOIN FETCH m.personalInfo
            LEFT JOIN FETCH s.type LEFT JOIN FETCH s.trainingPackage tp LEFT JOIN FETCH tp.courses
            JOIN FETCH s.status
            WHERE s.status.id = :activeStatusId
              AND s.endDate >= :today
              AND s.endDate <= :threshold
              AND (:gymId IS NULL OR s.member.gym.id = :gymId)
            ORDER BY s.endDate ASC
            """)
    List<Subscription> findExpiringSoon(
            @Param("today") LocalDate today,
            @Param("threshold") LocalDate threshold,
            @Param("activeStatusId") Long activeStatusId,
            @Param("gymId") Long gymId
    );

    /**
     * Bulk-expire: move all ACTIVE subscriptions whose endDate is before today
     * to the EXPIRED status.  Called nightly by {@code SubscriptionExpiryJob}.
     */
    @Modifying
    @Query("""
            UPDATE Subscription s
            SET s.status = :expiredStatus
            WHERE s.status.code = 'ACTIVE'
              AND s.endDate < :today
            """)
    int expireOverdueSubscriptions(
            @Param("today") LocalDate today,
            @Param("expiredStatus") SubscriptionStatus expiredStatus
    );

    // Gym-scoped the same way as findExpiringSoon below (pass gymId = null for ADMIN / all gyms).
    @Query("""
            SELECT DISTINCT s FROM Subscription s
            JOIN FETCH s.member m JOIN FETCH m.personalInfo
            LEFT JOIN FETCH s.type LEFT JOIN FETCH s.trainingPackage tp LEFT JOIN FETCH tp.courses
            JOIN FETCH s.status
            WHERE (:gymId IS NULL OR s.member.gym.id = :gymId)
            """)
    List<Subscription> findAllWithDetails(@Param("gymId") Long gymId);

    /**
     * True when the member has at least one ACTIVE subscription not yet expired
     * (endDate >= today). Used by check-in eligibility.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM Subscription s
            WHERE s.member.id = :memberId
              AND s.status.code = 'ACTIVE'
              AND s.endDate >= :today
            """)
    boolean hasActiveSubscription(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    /**
     * True when the member already has another subscription starting after {@code endDate} —
     * i.e. they've already renewed, so a renewal reminder for the one expiring on
     * {@code endDate} would be noise. Used by {@code SubscriptionRenewalReminderJob}.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM Subscription s
            WHERE s.member.id = :memberId AND s.startDate > :endDate
            """)
    boolean existsByMemberIdAndStartDateAfter(@Param("memberId") Long memberId, @Param("endDate") LocalDate endDate);

    /** Bulk-remove all subscriptions of a member (used when deleting the member; delete payments first). */
    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    /** How many subscriptions still reference a subscription type — guards its deletion. */
    long countByTypeId(Long typeId);
}
