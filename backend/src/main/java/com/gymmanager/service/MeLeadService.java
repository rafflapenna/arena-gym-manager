package com.gymmanager.service;

import com.gymmanager.dto.MeLeadRequestDTO;
import com.gymmanager.dto.MeLeadResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.entity.Course;
import com.gymmanager.entity.Lead;
import com.gymmanager.entity.LeadOrigin;
import com.gymmanager.entity.LeadStatus;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.FieldValidationException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.LeadRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.ReferralRewardRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Prove inserite dai soci" (2026-10-01): lets a MEMBER report a friend's free trial from
 * {@code /me/leads} instead of the reception always being the one to key it in. The lead
 * lands as {@code REQUESTED} and {@code referredBy} = the caller — both forced server-side,
 * never trusted from the body — and stays non-convertible until staff confirms or declines it
 * via {@code LeadController} ({@code POST /leads/{id}/confirm|decline}).
 *
 * <p>Deliberately a separate service/controller from the staff-side {@link LeadService} /
 * {@code LeadController}: the member-facing contract (narrower DTO, forced referrer/gym/status,
 * anti-abuse checks below) has nothing in common with the staff CRUD beyond the shared
 * {@link Lead} entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeLeadService {

    private final LeadRepository leadRepository;
    private final MemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final ReferralRewardRepository rewardRepository;
    private final CurrentUser currentUser;

    /** Anti-abuse cap (spec): at most this many open self-reported leads per member at once. */
    private static final int PENDING_LIMIT = 5;

    @Transactional
    public MeLeadResponseDTO create(MeLeadRequestDTO dto) {
        Member me = requireMember();
        Long gymId = me.getGym().getId();
        String normalizedPhone = normalizePhone(dto.getPhone());

        // 1) Can't report your own number (anti-abuse / nonsensical "I brought myself in").
        if (normalizedPhone.equals(normalizePhone(me.getPersonalInfo().getPhone()))) {
            throw new FieldValidationException(Map.of("phone", "Non puoi inserire il tuo numero di telefono"));
        }

        // 2) Per-member cap on open self-reported leads (never counts a lead staff logged with
        // this member as referrer — only origin=MEMBER does).
        long pendingCount = leadRepository.countByReferredByIdAndOriginAndStatusIn(
                me.getId(), LeadOrigin.MEMBER, LeadStatus.PENDING);
        if (pendingCount >= PENDING_LIMIT) {
            throw new ConflictException(
                    "Hai già 5 prove in attesa: potrai inserirne altre quando la reception le avrà gestite.",
                    "LEAD_PENDING_LIMIT");
        }

        // 3) No duplicate phone among the gym's currently-open leads (any origin).
        boolean duplicate = leadRepository.findPhonesByGymIdAndStatusIn(gymId, LeadStatus.OPEN).stream()
                .map(MeLeadService::normalizePhone)
                .anyMatch(normalizedPhone::equals);
        if (duplicate) {
            throw new ConflictException(
                    "Per questo numero di telefono c'è già una prova in corso.",
                    "LEAD_DUPLICATE_PHONE");
        }

        // 4) Optional interest course, must belong to the same gym.
        Course course = resolveInterestCourse(dto.getCourseId(), gymId);

        // 5) Does the phone already belong to an existing member of the gym? No error either
        // way (anti user-enumeration) — just a hint for staff, never shown to the reporter.
        Long matchedMemberId = findMatchedMemberId(gymId, me.getId(), normalizedPhone);

        Lead lead = Lead.builder()
                .gym(me.getGym())
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .phone(dto.getPhone().trim())
                .interestCourse(course)
                .trialDate(dto.getTrialDate())
                .referredBy(me)
                .origin(LeadOrigin.MEMBER)
                .status(LeadStatus.REQUESTED)
                .matchedMemberId(matchedMemberId)
                .build();

        Lead saved = leadRepository.save(lead);
        log.info("Member id={} reported trial lead id={} gymId={}", me.getId(), saved.getId(), gymId);
        return toDTO(saved, false);
    }

    /**
     * "I miei amici": every lead naming the caller as referrer, newest first. Exactly two
     * queries total — the lead list (EntityGraph on interestCourse) and the caller's rewards
     * (any status) — never N+1 per lead.
     */
    @Transactional(readOnly = true)
    public java.util.List<MeLeadResponseDTO> findMine() {
        Long memberId = currentUser.memberId();

        var leads = leadRepository.findByReferredByIdOrderByCreatedAtDesc(memberId);

        // Only .getId() is ever read off referredMember below — safe on a lazy proxy without
        // an extra round-trip per reward (see ReferralRewardRepository.findByBeneficiaryId).
        Set<Long> rewardedReferredMemberIds = rewardRepository.findByBeneficiaryId(memberId).stream()
                .map(r -> r.getReferredMember().getId())
                .collect(Collectors.toSet());

        return leads.stream()
                .map(lead -> toDTO(lead, lead.getConvertedMemberId() != null
                        && rewardedReferredMemberIds.contains(lead.getConvertedMemberId())))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Long findMatchedMemberId(Long gymId, Long selfMemberId, String normalizedPhone) {
        return memberRepository.findIdAndPhoneByGymId(gymId).stream()
                .filter(row -> !selfMemberId.equals((Long) row[0]))
                .filter(row -> normalizedPhone.equals(normalizePhone((String) row[1])))
                .map(row -> (Long) row[0])
                .findFirst()
                .orElse(null);
    }

    /** Cross-gym reference looks like not found (anti-IDOR), same pattern as LeadService. */
    private Course resolveInterestCourse(Long courseId, Long gymId) {
        if (courseId == null) return null;
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        Long courseGymId = course.getGym() != null ? course.getGym().getId() : null;
        if (!gymId.equals(courseGymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        return course;
    }

    private Member requireMember() {
        Long memberId = currentUser.memberId();
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not a member");
        }
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    }

    /**
     * Normalizes a phone number for equality comparison only (never stored): keep digits and a
     * leading {@code +}, turn a {@code 00} international prefix into {@code +}, then strip a
     * {@code +39} country code — so "+39 333 1234567" and "3331234567" compare equal.
     */
    static String normalizePhone(String phone) {
        if (phone == null) return "";
        String normalized = phone.replaceAll("[^0-9+]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        if (normalized.startsWith("+39")) {
            normalized = normalized.substring(3);
        }
        return normalized;
    }

    private MeLeadResponseDTO toDTO(Lead lead, boolean rewardEarned) {
        return MeLeadResponseDTO.builder()
                .id(lead.getId())
                .firstName(lead.getFirstName())
                .lastName(lead.getLastName())
                .status(lead.getStatus().name())
                .trialDate(lead.getTrialDate())
                .interestCourse(lead.getInterestCourse() != null
                        ? NamedRefDTO.builder()
                                .id(lead.getInterestCourse().getId())
                                .name(lead.getInterestCourse().getName())
                                .build()
                        : null)
                .rewardEarned(rewardEarned)
                .createdAt(lead.getCreatedAt())
                .build();
    }
}
