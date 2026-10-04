package com.gymmanager.repository;

import com.gymmanager.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * Single-entity lookup that eagerly joins {@code personalInfo}, its nested
     * {@code beltGrade}, and {@code gym} in one SQL statement.
     *
     * <p>Overrides the inherited {@code findById} so that every call to
     * {@code getMember} in {@code MemberService} avoids the three subsequent
     * lazy-load round-trips that would otherwise hit Neon (~20 ms each).
     */
    @Override
    @EntityGraph(attributePaths = {"personalInfo", "personalInfo.beltGrade", "gym"})
    Optional<Member> findById(Long id);

    long countByGymId(Long gymId);

    /**
     * Count of members of a gym whose registrationDate falls within a range (inclusive),
     * used by the dashboard "new members this month" card. Derived query is translated
     * to a single {@code COUNT(*)} in the DB — no entities are loaded into memory.
     */
    long countByGymIdAndRegistrationDateBetween(Long gymId, LocalDate from, LocalDate to);

    boolean existsByPersonalInfoEmail(String email);

    boolean existsByPersonalInfoEmailAndIdNot(String email, Long id);

    Optional<Member> findByPersonalInfoEmail(String email);

    /** Resolves the member behind a scanned QR check-in token (roadmap #5). */
    Optional<Member> findByCheckinToken(String checkinToken);

    /**
     * {id, phone} of every member of a gym, for {@code MeLeadService}'s matched-member check
     * on a self-reported lead ("prove inserite dai soci"): whether the phone the member typed
     * already belongs to someone. A plain projection rather than full {@code Member} entities
     * (no need for personalInfo/beltGrade/gym) — comparison happens in Java because the stored
     * phone format isn't canonical (see {@code MeLeadService.normalizePhone}).
     */
    @Query("SELECT m.id, pi.phone FROM Member m JOIN m.personalInfo pi WHERE m.gym.id = :gymId")
    List<Object[]> findIdAndPhoneByGymId(@Param("gymId") Long gymId);

    /**
     * Paginated member search.
     *
     * <p>{@code @EntityGraph} causes Hibernate to issue a single JOIN for
     * {@code personalInfo}, {@code personalInfo.beltGrade}, and {@code gym}
     * so that {@code MemberService.toDTO} never triggers lazy loads.
     * Using EntityGraph (not JOIN FETCH) avoids the HHH000104 in-memory
     * pagination warning that a JPQL fetch join would produce with Pageable.
     */
    @EntityGraph(attributePaths = {"personalInfo", "personalInfo.beltGrade", "gym"})
    @Query("""
            SELECT m FROM Member m JOIN m.personalInfo pi
            WHERE (:gymId IS NULL OR m.gym.id = :gymId)
              AND (:active IS NULL OR m.active = :active)
              AND (LENGTH(:pattern) = 0
                   OR LOWER(pi.firstName) LIKE :pattern ESCAPE '\\'
                   OR LOWER(pi.lastName)  LIKE :pattern ESCAPE '\\'
                   OR LOWER(pi.email)     LIKE :pattern ESCAPE '\\'
                   OR LOWER(pi.phone)     LIKE :pattern ESCAPE '\\')
            """)
    Page<Member> search(
            @Param("pattern") String pattern,
            @Param("active") Boolean active,
            @Param("gymId") Long gymId,
            Pageable pageable
    );

    /**
     * Detaches everyone this member brought in, so deleting them doesn't fail on the
     * self-referencing FK. The referred members survive: losing who introduced them is far
     * less bad than blocking the delete or cascading it onto real people.
     */
    @Modifying
    @Query("UPDATE Member m SET m.referredBy = null WHERE m.referredBy.id = :memberId")
    void clearReferredBy(@Param("memberId") Long memberId);
}
