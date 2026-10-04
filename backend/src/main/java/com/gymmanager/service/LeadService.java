package com.gymmanager.service;

import com.gymmanager.dto.LeadConversionResponseDTO;
import com.gymmanager.dto.LeadConvertRequestDTO;
import com.gymmanager.dto.LeadRequestDTO;
import com.gymmanager.dto.LeadResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.MemberResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.dto.LeadConfirmRequestDTO;
import com.gymmanager.entity.Course;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Lead;
import com.gymmanager.entity.LeadOrigin;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.LeadStatus;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.FieldValidationException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.LeadRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CRUD + conversion for per-gym leads (roadmap #8 "Prove gratuite"), on the same scope-gym
 * pattern as {@link CourseService} — not paginated (D6: prospect volume is low, unlike the
 * member roster).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final CourseRepository courseRepository;
    private final GymRepository gymRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final CurrentUser currentUser;

    @Transactional
    public LeadResponseDTO create(LeadRequestDTO dto) {
        Gym gym = requireGym();
        Course course = resolveInterestCourse(dto.getCourseId(), gym.getId());
        LeadStatus status = dto.getStatus() != null ? dto.getStatus() : deriveStatus(dto.getTrialDate());
        assertStatusNotRequested(status, null);

        Lead lead = Lead.builder()
                .gym(gym)
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .birthDate(dto.getBirthDate())
                .phone(dto.getPhone().trim())
                .email(normalizeEmail(dto.getEmail()))
                .source(dto.getSource())
                .interestCourse(course)
                .trialDate(dto.getTrialDate())
                .referredBy(resolveReferrer(dto.getReferredByMemberId(), gym.getId()))
                .origin(LeadOrigin.STAFF)
                .status(status)
                .notes(dto.getNotes())
                .build();

        Lead saved = leadRepository.save(lead);
        log.info("Created lead id={} gymId={} status={}", saved.getId(), gym.getId(), saved.getStatus());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LeadResponseDTO> findAll(String status, String q, String origin) {
        Long gymId = requireGym().getId();
        LeadStatus parsedStatus = parseStatus(status);
        LeadOrigin parsedOrigin = parseOrigin(origin);
        String pattern = StringUtils.hasText(q) ? "%" + escapeLike(q.toLowerCase()) + "%" : "";
        return leadRepository.search(gymId, parsedStatus, parsedOrigin, pattern).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public LeadResponseDTO findById(Long id) {
        return toDTO(getLead(id));
    }

    @Transactional
    public LeadResponseDTO update(Long id, LeadRequestDTO dto) {
        Lead lead = getLead(id);
        Course course = resolveInterestCourse(dto.getCourseId(), lead.getGym().getId());

        lead.setFirstName(dto.getFirstName().trim());
        lead.setLastName(dto.getLastName().trim());
        lead.setBirthDate(dto.getBirthDate());
        lead.setPhone(dto.getPhone().trim());
        lead.setEmail(normalizeEmail(dto.getEmail()));
        lead.setSource(dto.getSource());
        lead.setInterestCourse(course);
        lead.setTrialDate(dto.getTrialDate());
        // A MEMBER-origin lead's referrer is the member who reported it, forced server-side at
        // creation (see MeLeadService.create) — never editable from here, so an update request
        // can't silently detach a self-reported lead from its referrer (or reattach it to
        // someone else). dto.getReferredByMemberId() is simply ignored in that case.
        if (lead.getOrigin() != LeadOrigin.MEMBER) {
            lead.setReferredBy(resolveReferrer(dto.getReferredByMemberId(), lead.getGym().getId()));
        }
        // Unlike create, an omitted status on update is NOT re-derived from trialDate: doing
        // so would silently overwrite a manually chosen status (e.g. LOST) on every unrelated
        // edit whose form doesn't round-trip a status field. Same "leave unchanged if absent"
        // idiom as CourseService.update's handling of the optional `active` flag.
        if (dto.getStatus() != null) {
            assertStatusNotRequested(dto.getStatus(), lead.getStatus());
            lead.setStatus(dto.getStatus());
        }
        lead.setNotes(dto.getNotes());

        log.info("Updated lead id={}", id);
        return toDTO(leadRepository.save(lead));
    }

    @Transactional
    public void delete(Long id) {
        Lead lead = getLead(id);
        leadRepository.delete(lead);
        log.info("Deleted lead id={}", id);
    }

    /**
     * Reception confirms a self-reported trial ("prove inserite dai soci"): picks/accepts the
     * trial date and moves the lead out of REQUESTED. Only a REQUESTED lead can be confirmed —
     * calling it again, or on a lead some other path already moved on, is a conflict rather
     * than a silent no-op, since the trial date would otherwise be overwritten unexpectedly.
     */
    @Transactional
    public LeadResponseDTO confirm(Long id, LeadConfirmRequestDTO dto) {
        Lead lead = getLead(id);
        assertRequested(lead);

        lead.setTrialDate(dto.getTrialDate());
        lead.setStatus(LeadStatus.TRIAL_SCHEDULED);

        log.info("Confirmed lead id={} trialDate={}", id, dto.getTrialDate());
        return toDTO(leadRepository.save(lead));
    }

    /** Reception rejects a self-reported trial ("prove inserite dai soci"). */
    @Transactional
    public LeadResponseDTO decline(Long id) {
        Lead lead = getLead(id);
        assertRequested(lead);

        lead.setStatus(LeadStatus.LOST);

        log.info("Declined lead id={}", id);
        return toDTO(leadRepository.save(lead));
    }

    /**
     * Converts a lead into a paying member (R4/D5): creates the {@link com.gymmanager.entity.Member}
     * via {@link MemberService#create} (reusing all of its validation/normalization/409-on-duplicate-
     * email behavior) and links it back onto the lead, in the same transaction so a failure on
     * either side rolls back both — never an orphaned member.
     */
    @Transactional
    public LeadConversionResponseDTO convert(Long id, LeadConvertRequestDTO dto) {
        Lead lead = getLead(id);

        if (lead.getStatus() == LeadStatus.CONVERTED) {
            // Idempotent replay (D5): the irreversible step (member creation) already
            // happened. Ignore the body's email and return the lead + its linked member so
            // the FE can safely retry a convert call whose response was lost in transit.
            MemberResponseDTO member = lead.getConvertedMemberId() != null
                    ? memberService.findById(lead.getConvertedMemberId())
                    : null;
            return LeadConversionResponseDTO.builder().lead(toDTO(lead)).member(member).build();
        }

        // A self-reported lead ("prove inserite dai soci") must be confirmed by the reception
        // (-> TRIAL_SCHEDULED/TRIED/etc.) before it can become a member — REQUESTED means
        // nobody at the gym has even looked at it yet.
        if (lead.getStatus() == LeadStatus.REQUESTED) {
            throw new ConflictException(
                    "La prova deve essere confermata dalla reception prima dell'iscrizione.",
                    "LEAD_NOT_CONFIRMED");
        }

        // The lead's own birthDate is null only for a MEMBER-origin lead the reception hasn't
        // completed yet (see Lead.birthDate) — fall back to the one typed into this convert
        // call, and persist it onto the lead so a later idempotent replay still has it.
        LocalDate birthDate = lead.getBirthDate() != null ? lead.getBirthDate() : dto.getBirthDate();
        if (birthDate == null) {
            throw new FieldValidationException(
                    Map.of("birthDate", "La data di nascita è obbligatoria per iscrivere il socio"));
        }
        if (lead.getBirthDate() == null) {
            lead.setBirthDate(birthDate);
        }

        PersonalInfoRequestDTO personalInfo = new PersonalInfoRequestDTO();
        personalInfo.setFirstName(lead.getFirstName());
        personalInfo.setLastName(lead.getLastName());
        personalInfo.setBirthDate(birthDate);
        personalInfo.setPhone(lead.getPhone());
        personalInfo.setEmail(dto.getEmail());

        MemberRequestDTO memberRequest = new MemberRequestDTO();
        memberRequest.setPersonalInfo(personalInfo);
        // Who brought them in was recorded at the reception desk on the trial; carry it over,
        // because it is on the Member that it earns the referrer their discount.
        memberRequest.setReferredByMemberId(
                lead.getReferredBy() != null ? lead.getReferredBy().getId() : null);
        memberRequest.setNotes(lead.getNotes());
        memberRequest.setRegistrationDate(LocalDate.now());

        // Propagates ConflictException (409) from MemberService.create when the email is
        // already used by another member — must NOT be caught/swallowed here (spec §3.4).
        MemberResponseDTO member = memberService.create(memberRequest);

        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedMemberId(member.getId());
        Lead saved = leadRepository.save(lead);

        log.info("Converted lead id={} -> member id={}", id, member.getId());
        return LeadConversionResponseDTO.builder().lead(toDTO(saved)).member(member).build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Default status when the request omits one (DESIGN-prove-lead.it.md §3): no trial date
     * -> NEW; a future trial date -> TRIAL_SCHEDULED; today or past -> TRIED. The operator can
     * always override by supplying status explicitly.
     */
    private static LeadStatus deriveStatus(LocalDate trialDate) {
        if (trialDate == null) return LeadStatus.NEW;
        return trialDate.isAfter(LocalDate.now()) ? LeadStatus.TRIAL_SCHEDULED : LeadStatus.TRIED;
    }

    private static String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.toLowerCase().trim() : null;
    }

    /**
     * Parses the {@code ?status=} query filter. An invalid value must raise a
     * {@link ResponseStatusException} (handled by GlobalExceptionHandler -> clean 4xx) rather
     * than let {@code LeadStatus.valueOf}'s IllegalArgumentException escape unhandled -> 500.
     */
    private static LeadStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        try {
            return LeadStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lead status: " + status);
        }
    }

    /** Parses the {@code ?origin=} query filter — same "clean 400, never a 500" reasoning as {@link #parseStatus}. */
    private static LeadOrigin parseOrigin(String origin) {
        if (!StringUtils.hasText(origin)) return null;
        try {
            return LeadOrigin.valueOf(origin.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lead origin: " + origin);
        }
    }

    /**
     * REQUESTED is reserved for a self-reported lead fresh off {@code POST /me/leads} — staff
     * can never set it directly on create, nor on update unless the lead is already REQUESTED
     * (i.e. editing some other field of a not-yet-reviewed lead without touching its status).
     */
    private static void assertStatusNotRequested(LeadStatus requestedStatus, LeadStatus currentStatus) {
        if (requestedStatus == LeadStatus.REQUESTED && currentStatus != LeadStatus.REQUESTED) {
            throw new FieldValidationException(Map.of(
                    "status", "Lo stato REQUESTED è riservato alle prove inserite dai soci"));
        }
    }

    private static void assertRequested(Lead lead) {
        if (lead.getStatus() != LeadStatus.REQUESTED) {
            throw new ConflictException(
                    "La prova non è più in attesa di conferma.", "LEAD_NOT_REQUESTED");
        }
    }

    /** Same LIKE-metacharacter escaping as {@code MemberService.escapeLike}. */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Lead getLead(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));
        assertInScope(lead);
        return lead;
    }

    /** The member who brought this prospect in; cross-gym looks like not found (anti-IDOR). */
    private Member resolveReferrer(Long referrerId, Long gymId) {
        if (referrerId == null) return null;
        Member referrer = memberRepository.findById(referrerId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", referrerId));
        Long referrerGymId = referrer.getGym() != null ? referrer.getGym().getId() : null;
        if (!gymId.equals(referrerGymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        return referrer;
    }

    private Course resolveInterestCourse(Long courseId, Long gymId) {
        if (courseId == null) return null;
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        Long courseGymId = course.getGym() != null ? course.getGym().getId() : null;
        if (!gymId.equals(courseGymId)) {
            // Cross-gym reference: treat as not found (anti-IDOR), consistent with CourseService.resolveDiscipline.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        return course;
    }

    /** Cross-gym access looks like "not found" (anti-IDOR), same pattern as CourseService. */
    private void assertInScope(Lead lead) {
        Long gymId = lead.getGym() != null ? lead.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found");
        }
    }

    private Gym requireGym() {
        Long gymId = currentUser.gymId();
        if (gymId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        return gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym", gymId));
    }

    private LeadResponseDTO toDTO(Lead l) {
        return LeadResponseDTO.builder()
                .id(l.getId())
                .firstName(l.getFirstName())
                .lastName(l.getLastName())
                .birthDate(l.getBirthDate())
                .phone(l.getPhone())
                .email(l.getEmail())
                .source(l.getSource())
                .interestCourse(l.getInterestCourse() != null
                        ? NamedRefDTO.builder().id(l.getInterestCourse().getId()).name(l.getInterestCourse().getName()).build()
                        : null)
                .trialDate(l.getTrialDate())
                .status(l.getStatus().name())
                .notes(l.getNotes())
                .createdAt(l.getCreatedAt())
                .convertedMemberId(l.getConvertedMemberId())
                .referredBy(l.getReferredBy() != null
                        ? NamedRefDTO.builder()
                                .id(l.getReferredBy().getId())
                                .name(MemberService.fullName(l.getReferredBy()))
                                .build()
                        : null)
                .origin(l.getOrigin().name())
                .matchedMemberId(l.getMatchedMemberId())
                .build();
    }
}
