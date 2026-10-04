package com.gymmanager.repository;

import com.gymmanager.entity.MemberDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberDocumentRepository extends JpaRepository<MemberDocument, Long> {

    List<MemberDocument> findByMemberIdOrderByUploadedAtDesc(Long memberId);

    /** Anti-IDOR: a document only resolves when it belongs to the given member. */
    Optional<MemberDocument> findByIdAndMemberId(Long id, Long memberId);

    /**
     * MEDICAL_CERT documents expiring within [today, threshold], optionally scoped to a
     * single gym (pass {@code gymId = null} for all gyms). Mirrors
     * {@code SubscriptionRepository.findExpiringSoon}.
     */
    @Query("""
            SELECT d FROM MemberDocument d
            JOIN FETCH d.member m JOIN FETCH m.personalInfo
            WHERE d.type = com.gymmanager.entity.DocumentType.MEDICAL_CERT
              AND d.expiryDate IS NOT NULL
              AND d.expiryDate >= :today
              AND d.expiryDate <= :threshold
              AND (:gymId IS NULL OR m.gym.id = :gymId)
            ORDER BY d.expiryDate ASC
            """)
    List<MemberDocument> findExpiringMedicalCerts(
            @Param("today") LocalDate today,
            @Param("threshold") LocalDate threshold,
            @Param("gymId") Long gymId
    );

    /** Bulk-remove all documents of a member (used when deleting the member). */
    @Modifying
    @Query("DELETE FROM MemberDocument d WHERE d.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
