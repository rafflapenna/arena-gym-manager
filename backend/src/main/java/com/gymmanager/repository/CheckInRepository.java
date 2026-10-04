package com.gymmanager.repository;

import com.gymmanager.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Query("""
            SELECT c FROM CheckIn c
            JOIN FETCH c.member m JOIN FETCH m.personalInfo
            WHERE c.member.id = :memberId
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findByMemberIdWithDetails(@Param("memberId") Long memberId);

    @Query("""
            SELECT c FROM CheckIn c
            JOIN FETCH c.member m JOIN FETCH m.personalInfo
            WHERE m.gym.id = :gymId
              AND c.checkInAt >= :start AND c.checkInAt < :end
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findByGymForRange(
            @Param("gymId") Long gymId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT c FROM CheckIn c
            JOIN FETCH c.member m JOIN FETCH m.personalInfo
            WHERE c.trainingClassId = :trainingClassId
              AND c.checkInAt >= :start AND c.checkInAt < :end
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findByTrainingClassIdForRange(
            @Param("trainingClassId") Long trainingClassId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Existing check-in(s) for a member within [start, end). Used for same-day
     * idempotency: at most one entry per member per day in Tappa 1. Returns a list
     * (rather than Optional) defensively, in case legacy rows ever overlap a window.
     */
    @Query("""
            SELECT c FROM CheckIn c
            JOIN FETCH c.member m JOIN FETCH m.personalInfo
            WHERE c.member.id = :memberId
              AND c.checkInAt >= :start AND c.checkInAt < :end
            ORDER BY c.checkInAt ASC
            """)
    List<CheckIn> findForMemberInRange(
            @Param("memberId") Long memberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /** Bulk-remove all check-ins of a member (used when deleting the member). */
    @Modifying
    @Query("DELETE FROM CheckIn c WHERE c.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
