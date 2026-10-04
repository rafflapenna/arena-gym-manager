package com.gymmanager.repository;

import com.gymmanager.entity.MedicalCertReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalCertReminderLogRepository extends JpaRepository<MedicalCertReminderLog, Long> {

    // NOT a derived-query method name: Spring Data parses "...DaysBefore" as the property
    // "days" + the "Before" comparison keyword (used for date/number range queries), not as
    // the literal field "daysBefore" - it fails at startup with "No property 'days' found".
    // Hence the explicit JPQL.
    @Query("""
            SELECT COUNT(l) > 0 FROM MedicalCertReminderLog l
            WHERE l.document.id = :documentId AND l.daysBefore = :daysBefore
            """)
    boolean existsByDocumentIdAndDaysBefore(@Param("documentId") Long documentId, @Param("daysBefore") int daysBefore);

    /** Bulk-remove all reminder logs for a member's documents (used when deleting the member). */
    @Modifying
    @Query("""
            DELETE FROM MedicalCertReminderLog l
            WHERE l.document.id IN (SELECT d.id FROM MemberDocument d WHERE d.member.id = :memberId)
            """)
    void deleteByMemberId(@Param("memberId") Long memberId);
}
