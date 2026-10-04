package com.gymmanager.service;

import com.gymmanager.dto.CheckInResponseDTO;
import com.gymmanager.entity.CheckIn;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Attendance check-in (Tappa 1 — single access per day).
 *
 * <p>Idempotent within the calendar day: a member can only register one check-in
 * per day, so a re-click returns the existing entry instead of duplicating it.
 *
 * <p>Eligibility (an active subscription) is handled asymmetrically by channel:
 * an operator ({@code RECEPTION}) is never blocked and only sees warnings, while a
 * member's self check-in ({@code SELF}) is rejected when not eligible — they must
 * go through the reception.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CurrentUser currentUser;
    private final CheckInInsertService checkInInsertService;

    @Transactional
    public CheckInResponseDTO checkIn(Long memberId, CheckInSource source) {
        return checkIn(memberId, source, null);
    }

    /**
     * @param trainingClassId course this check-in is attributed to, or {@code null} when the
     *                        channel has no course context (plain reception/self check-in).
     *                        Only the trainer-marks-attendance flow currently supplies this.
     */
    @Transactional
    public CheckInResponseDTO checkIn(Long memberId, CheckInSource source, Long trainingClassId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        assertMemberInScope(member);

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime nextDayStart = today.plusDays(1).atStartOfDay();

        // Idempotency: one check-in per member per day. A re-click (from either channel)
        // returns the existing entry rather than creating a duplicate.
        List<CheckIn> existing = checkInRepository.findForMemberInRange(memberId, dayStart, nextDayStart);
        if (!existing.isEmpty()) {
            return backfillAndReturnExisting(existing.get(0), trainingClassId, source, memberId, today);
        }

        List<String> warnings = computeWarnings(memberId, today);

        // Self check-in is gated on eligibility: a non-eligible member must use the reception.
        if (source == CheckInSource.SELF && !warnings.isEmpty()) {
            throw new ConflictException("Abbonamento non attivo. Rivolgiti alla reception per l'ingresso.");
        }

        CheckIn checkIn = CheckIn.builder()
                .member(member)
                .gym(member.getGym())
                .checkInAt(LocalDateTime.now())
                .source(source)
                .trainingClassId(trainingClassId)
                .createdBy(currentUser.get().userId())
                .build();

        CheckIn saved;
        try {
            // Own transaction (see CheckInInsertService): a same-day race that slips past the
            // check above now hits the ux_check_ins_member_day unique index (round 3 #4) here.
            saved = checkInInsertService.insert(checkIn);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request for this exact (member, day) won and committed first.
            // Degrade gracefully instead of propagating a generic 409: this transaction/
            // connection was never touched by the failed insert (it ran on its own), so it's
            // safe to re-query and hand back the winning row as alreadyCheckedIn, same as the
            // sequential idempotency path above.
            log.info("Check-in race for memberId={}: another request won, falling back to its entry", memberId);
            List<CheckIn> winner = checkInRepository.findForMemberInRange(memberId, dayStart, nextDayStart);
            if (winner.isEmpty()) {
                // The unique index only trips if a colliding row already exists — this would
                // mean some other, unrelated constraint failed. Don't silently swallow that.
                throw e;
            }
            return backfillAndReturnExisting(winner.get(0), trainingClassId, source, memberId, today);
        }

        log.info("Created check-in id={} memberId={} source={} warnings={}",
                saved.getId(), memberId, source, warnings.size());
        return toDTO(saved, warnings, false);
    }

    /**
     * Attribution repair: a member who already checked in today at reception (no course
     * context) and is then marked present in a course by the trainer must not silently lose
     * that course link — the entry would otherwise never show up in the trainer's attendance
     * list. Only backfills a missing trainingClassId; never overwrites one already set (e.g. a
     * second, different course) to avoid one trainer stealing another's attendance record.
     * Shared by the sequential idempotency path and the concurrent-race recovery path.
     */
    private CheckInResponseDTO backfillAndReturnExisting(
            CheckIn existingCheckIn, Long trainingClassId, CheckInSource source, Long memberId, LocalDate today) {
        if (trainingClassId != null && existingCheckIn.getTrainingClassId() == null) {
            existingCheckIn.setTrainingClassId(trainingClassId);
            existingCheckIn.setSource(source);
            existingCheckIn = checkInRepository.save(existingCheckIn);
            log.info("Attributed existing check-in id={} memberId={} to trainingClassId={}",
                    existingCheckIn.getId(), memberId, trainingClassId);
        }
        return toDTO(existingCheckIn, computeWarnings(memberId, today), true);
    }

    /**
     * QR check-in (roadmap #5): resolves the {@link Member} from the scanned token and
     * delegates to {@link #checkIn(Long, CheckInSource, Long)} as {@code RECEPTION}'s sibling
     * channel {@code QR}, inheriting daily idempotency, non-blocking eligibility (an operator
     * scanning is never blocked, unlike {@code SELF}), and the {@code assertMemberInScope}
     * anti-IDOR check (unknown or cross-gym token both surface as a 404).
     */
    @Transactional
    public CheckInResponseDTO checkInByToken(String token) {
        Member member = memberRepository.findByCheckinToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found for the given token"));
        return checkIn(member.getId(), CheckInSource.QR, null);
    }

    @Transactional(readOnly = true)
    public List<CheckInResponseDTO> findByMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        assertMemberInScope(member);
        return checkInRepository.findByMemberIdWithDetails(memberId)
                .stream().map(c -> toDTO(c, List.of(), false)).toList();
    }

    @Transactional(readOnly = true)
    public List<CheckInResponseDTO> findByGymOnDate(LocalDate date) {
        Long gymId = currentUser.gymId();
        if (gymId == null) {
            // ADMIN has no gym and is excluded from this area by security anyway.
            return List.of();
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return checkInRepository.findByGymForRange(gymId, start, end)
                .stream().map(c -> toDTO(c, List.of(), false)).toList();
    }

    /** Check-ins attributed to a specific course (trainingClassId) on a given date. */
    @Transactional(readOnly = true)
    public List<CheckInResponseDTO> findByCourseOnDate(Long courseId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return checkInRepository.findByTrainingClassIdForRange(courseId, start, end)
                .stream().map(c -> toDTO(c, List.of(), false)).toList();
    }

    /** Non-blocking eligibility notices for a member (currently: active subscription). */
    private List<String> computeWarnings(Long memberId, LocalDate today) {
        List<String> warnings = new ArrayList<>();
        if (!subscriptionRepository.hasActiveSubscription(memberId, today)) {
            warnings.add("Nessun abbonamento attivo o abbonamento scaduto.");
        }
        return warnings;
    }

    private void assertMemberInScope(Member member) {
        Long gymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    private CheckInResponseDTO toDTO(CheckIn c, List<String> warnings, boolean alreadyCheckedIn) {
        return CheckInResponseDTO.builder()
                .id(c.getId())
                .memberId(c.getMember().getId())
                .memberFullName(c.getMember().getPersonalInfo().getFirstName() + " "
                        + c.getMember().getPersonalInfo().getLastName())
                .checkInAt(c.getCheckInAt())
                .source(c.getSource().name())
                .outcome(warnings.isEmpty() ? "OK" : "WARNING")
                .warnings(warnings)
                .alreadyCheckedIn(alreadyCheckedIn)
                .build();
    }
}
