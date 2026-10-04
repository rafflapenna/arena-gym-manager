package com.gymmanager.repository;

import com.gymmanager.entity.Lead;
import com.gymmanager.entity.LeadOrigin;
import com.gymmanager.entity.LeadStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * Unpaginated filtered search (D6 — lead volume is expected to be low, unlike the member
     * roster): optional status filter, optional origin filter, optional free-text match on
     * name/phone/email. Same idiom as {@code MemberRepository.search} ({@code (:x IS NULL OR
     * …)} + escaped LIKE) — except the gym filter is a plain {@code =} here, never
     * {@code IS NULL}: unlike members, leads have no ADMIN "sees everything across tenants"
     * case, so gymId is always resolved from the current STAFF/GYM_MANAGER user, never absent.
     *
     * <p>{@code @EntityGraph} eagerly joins {@code interestCourse} and {@code referredBy} (+
     * its {@code personalInfo}) so {@code LeadService.toDTO} (which reads
     * {@code interestCourse.getName()} and {@code MemberService.fullName(referredBy)} for
     * every row) doesn't trigger an N+1 lazy load per lead — same reasoning as
     * {@code MemberRepository.search}'s EntityGraph. {@code referredBy} matters more now that
     * "prove inserite dai soci" leads always carry one.
     */
    @EntityGraph(attributePaths = {"interestCourse", "referredBy", "referredBy.personalInfo"})
    @Query("""
            SELECT l FROM Lead l
            WHERE l.gym.id = :gymId
              AND (:status IS NULL OR l.status = :status)
              AND (:origin IS NULL OR l.origin = :origin)
              AND (LENGTH(:pattern) = 0
                   OR LOWER(l.firstName) LIKE :pattern ESCAPE '\\'
                   OR LOWER(l.lastName)  LIKE :pattern ESCAPE '\\'
                   OR LOWER(l.phone)     LIKE :pattern ESCAPE '\\'
                   OR LOWER(l.email)     LIKE :pattern ESCAPE '\\')
            ORDER BY l.createdAt DESC
            """)
    List<Lead> search(
            @Param("gymId") Long gymId,
            @Param("status") LeadStatus status,
            @Param("origin") LeadOrigin origin,
            @Param("pattern") String pattern
    );

    /**
     * "I miei amici" (roadmap: prove inserite dai soci): every lead that names this member as
     * referrer, whoever created it (the member via {@code /me/leads}, or staff at the
     * reception desk). {@code @EntityGraph} on {@code interestCourse} keeps
     * {@code MeLeadService.toDTO} at one query for the whole list.
     */
    @EntityGraph(attributePaths = {"interestCourse"})
    List<Lead> findByReferredByIdOrderByCreatedAtDesc(Long referredById);

    /**
     * How many open self-reported leads a member currently has (anti-abuse cap). Deliberately
     * scoped to {@code origin = MEMBER}: a lead staff logged with this member as referrer
     * never counts against the member's own cap.
     */
    long countByReferredByIdAndOriginAndStatusIn(
            Long referredById, LeadOrigin origin, Collection<LeadStatus> statuses);

    /**
     * Phone numbers of every currently-open lead in the gym (any origin), for the
     * duplicate-phone check on a new member-reported lead. A plain projection, not full
     * entities: normalizing and comparing phones happens in Java ({@code MeLeadService}; see
     * its {@code normalizePhone}) since the stored format isn't canonical, so there is nothing
     * further for SQL to filter on.
     */
    @Query("SELECT l.phone FROM Lead l WHERE l.gym.id = :gymId AND l.status IN :statuses")
    List<String> findPhonesByGymIdAndStatusIn(
            @Param("gymId") Long gymId, @Param("statuses") Collection<LeadStatus> statuses);
}
