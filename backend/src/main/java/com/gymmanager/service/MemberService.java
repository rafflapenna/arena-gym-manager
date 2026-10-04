package com.gymmanager.service;

import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.MemberResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.PagedResponseDTO;
import com.gymmanager.dto.PersonalInfoResponseDTO;
import com.gymmanager.entity.BeltGrade;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MemberDocument;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.BeltGradeRepository;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.InviteTokenRepository;
import com.gymmanager.repository.MedicalCertReminderLogRepository;
import com.gymmanager.repository.MemberDocumentBlobRepository;
import com.gymmanager.repository.MemberDocumentRepository;
import com.gymmanager.repository.MemberFederationCardRepository;
import com.gymmanager.repository.MemberFeeRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.ReferralRewardRepository;
import com.gymmanager.repository.PaymentRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberFeeRepository memberFeeRepository;
    private final MemberFederationCardRepository memberFederationCardRepository;
    private final ReferralRewardRepository referralRewardRepository;
    private final BeltGradeRepository beltGradeRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CheckInRepository checkInRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final MemberDocumentBlobRepository memberDocumentBlobRepository;
    private final MedicalCertReminderLogRepository medicalCertReminderLogRepository;
    private final CurrentUser currentUser;

    private static final int MAX_PAGE_SIZE = 200;

    /** Gym filter for the current request: null for ADMIN (sees all), own gym otherwise. */
    private Long scopedGymId() {
        return currentUser.isAdmin() ? null : currentUser.gymId();
    }

    /**
     * Escapes SQL LIKE metacharacters ({@code %}, {@code _}) and the escape character itself
     * ({@code \}) so a free-text search term is matched literally instead of as a wildcard
     * pattern — e.g. searching "50%" or "a_b" must not match unrelated rows. Paired with
     * {@code ESCAPE '\'} on every LIKE clause in {@code MemberRepository.search}.
     */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public PagedResponseDTO<MemberResponseDTO> findAll(
            String query, Boolean active, int page, int size, String sortBy, String sortDir
    ) {
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        String actualSortBy = switch (sortBy) {
            case "firstName", "lastName", "email", "phone", "birthDate", "city" -> "personalInfo." + sortBy;
            default -> sortBy;
        };

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(actualSortBy).descending()
                : Sort.by(actualSortBy).ascending();

        String pattern = StringUtils.hasText(query) ? "%" + escapeLike(query.toLowerCase()) + "%" : "";
        Page<Member> result = memberRepository.search(pattern, active, scopedGymId(), PageRequest.of(page, effectiveSize, sort));

        return PagedResponseDTO.<MemberResponseDTO>builder()
                .content(result.getContent().stream().map(this::toDTO).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    public MemberResponseDTO findById(Long id) {
        return toDTO(getMember(id));
    }

    @Transactional
    public MemberResponseDTO create(MemberRequestDTO dto) {
        var pi = dto.getPersonalInfo();
        // Normalized the same way buildPersonalInfo persists it (lowercase+trim), otherwise a
        // duplicate that only differs in case skips this 409 and dies on the DB constraint instead.
        String email = pi.getEmail().toLowerCase().trim();
        if (memberRepository.existsByPersonalInfoEmail(email)) {
            throw new ConflictException("A member with email already exists: " + email);
        }

        Member member = Member.builder()
                .personalInfo(buildPersonalInfo(pi, new PersonalInfo()))
                .gym(resolveCurrentGym())
                .registrationDate(dto.getRegistrationDate())
                .active(true)
                .notes(dto.getNotes())
                .referredBy(resolveReferrer(dto.getReferredByMemberId(), null))
                .build();

        member = memberRepository.save(member);
        log.info("Created member id={} email={}", member.getId(), member.getPersonalInfo().getEmail());
        return toDTO(member);
    }

    @Transactional
    public MemberResponseDTO update(Long id, MemberRequestDTO dto) {
        Member member = getMember(id);
        var pi = dto.getPersonalInfo();

        // Normalized the same way buildPersonalInfo persists it (lowercase+trim), otherwise a
        // duplicate that only differs in case skips this 409 and dies on the DB constraint instead.
        String newEmail = pi.getEmail().toLowerCase().trim();
        if (memberRepository.existsByPersonalInfoEmailAndIdNot(newEmail, id)) {
            throw new ConflictException("A member with email already exists: " + newEmail);
        }

        buildPersonalInfo(pi, member.getPersonalInfo());
        if (dto.getRegistrationDate() != null) member.setRegistrationDate(dto.getRegistrationDate());
        member.setNotes(dto.getNotes());
        // An operator correction. It does NOT move a reward already earned: that one was
        // minted for whoever was recorded at the time and stays with them (a reward already
        // spent could not be moved anyway).
        member.setReferredBy(resolveReferrer(dto.getReferredByMemberId(), id));
        syncLinkedAccountUsername(id, newEmail);

        log.info("Updated member id={}", id);
        return toDTO(memberRepository.save(member));
    }

    /** Keeps the member's login account (if any) in sync with its email, since the
     *  username IS the login email. Without this, changing a member's email leaves
     *  the linked AppUser logging in / receiving mail at the stale address. */
    private void syncLinkedAccountUsername(Long memberId, String newEmail) {
        userRepository.findByMemberId(memberId).ifPresent(user -> {
            if (!newEmail.equalsIgnoreCase(user.getUsername())) {
                if (userRepository.existsByUsernameIgnoreCase(newEmail)) {
                    throw new ConflictException("An account already exists with email: " + newEmail);
                }
                user.setUsername(newEmail);
                userRepository.save(user);
                log.info("Synced linked account username for member id={} -> {}", memberId, newEmail);
            }
        });
    }

    @Transactional
    public MemberResponseDTO updateStatus(Long id, boolean active) {
        Member member = getMember(id);
        member.setActive(active);
        log.info("Member id={} active={}", id, active);
        return toDTO(memberRepository.save(member));
    }

    @Transactional
    public void delete(Long id) {
        Member member = getMember(id);

        // Remove the operational records that FK-reference the member, in dependency
        // order (payments -> subscriptions -> check-ins), or the members FK blocks the
        // delete. This makes "delete member" a full hard-delete of the member's data.
        // Referral data comes first: rewards FK-reference both members and the subscription
        // they were spent on, so they'd block the two deletes below.
        referralRewardRepository.deleteByMemberId(id);
        memberRepository.clearReferredBy(id);
        memberFeeRepository.deleteByMemberId(id);
        memberFederationCardRepository.deleteByMemberId(id);

        paymentRepository.deleteByMemberId(id);
        subscriptionRepository.deleteByMemberId(id);
        checkInRepository.deleteByMemberId(id);

        // Documents: reminder logs (FK -> document) first, then each blob (best-effort — the
        // blob store may be non-transactional, e.g. R2/S3, so a rollback here wouldn't undo
        // it anyway), then the metadata rows.
        List<MemberDocument> documents = memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(id);
        medicalCertReminderLogRepository.deleteByMemberId(id);
        documents.forEach(doc -> memberDocumentBlobRepository.deleteById(doc.getStorageKey()));
        memberDocumentRepository.deleteByMemberId(id);

        // A member may have a linked app account (the "abilita accesso app" flow).
        // Remove it — and its invite tokens — before the member row.
        userRepository.findByMemberId(id).ifPresent(user -> {
            inviteTokenRepository.deleteByUser(user);
            userRepository.delete(user);
            log.info("Deleted linked app account id={} for member id={}", user.getId(), id);
        });
        memberRepository.delete(member);
        log.info("Deleted member id={}", id);
    }

    private PersonalInfo buildPersonalInfo(com.gymmanager.dto.PersonalInfoRequestDTO dto, PersonalInfo target) {
        target.setFirstName(dto.getFirstName().trim());
        target.setLastName(dto.getLastName().trim());
        target.setEmail(dto.getEmail().toLowerCase().trim());
        target.setPhone(dto.getPhone());
        target.setBirthDate(dto.getBirthDate());
        target.setBirthPlace(dto.getBirthPlace());
        target.setFiscalCode(dto.getFiscalCode());
        target.setGender(dto.getGender());
        target.setAddress(dto.getAddress());
        target.setCity(dto.getCity());
        target.setPostalCode(dto.getPostalCode());
        target.setProvince(dto.getProvince());
        target.setRegion(dto.getRegion());
        if (dto.getCountry() != null) target.setCountry(dto.getCountry());

        if (dto.getBeltGradeId() != null) {
            BeltGrade grade = beltGradeRepository.findById(dto.getBeltGradeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Belt grade", dto.getBeltGradeId()));
            target.setBeltGrade(grade);
        } else {
            target.setBeltGrade(null);
        }
        return target;
    }

    /**
     * Resolves the optional "brought in by" member. A referrer from another gym is reported as
     * not found (anti-IDOR, same as everywhere else) rather than forbidden; referring yourself
     * is a plain validation error.
     *
     * @param selfId id of the member being edited, or null on create (nothing to compare yet)
     */
    private Member resolveReferrer(Long referrerId, Long selfId) {
        if (referrerId == null) return null;
        if (selfId != null && referrerId.equals(selfId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A member cannot be referred by themselves");
        }
        Member referrer = memberRepository.findById(referrerId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", referrerId));
        assertSameGym(referrer);
        return referrer;
    }

    static String fullName(Member m) {
        var pi = m.getPersonalInfo();
        if (pi == null) return "";
        return (pi.getFirstName() + " " + pi.getLastName()).trim();
    }

    private Member getMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", id));
        assertSameGym(member);
        return member;
    }

    private Gym resolveCurrentGym() {
        Long gymId = currentUser.gymId();
        if (gymId == null) return null;
        return gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym", gymId));
    }

    /** Non-admin users can only touch members of their own gym. */
    private void assertSameGym(Member member) {
        if (currentUser.isAdmin()) return;
        Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
        if (memberGymId == null || !memberGymId.equals(currentUser.gymId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    private MemberResponseDTO toDTO(Member m) {
        PersonalInfo pi = m.getPersonalInfo();
        LookupDTO beltGradeDTO = pi.getBeltGrade() != null
                ? LookupDTO.builder()
                        .id(pi.getBeltGrade().getId())
                        .code(pi.getBeltGrade().getCode())
                        .label(pi.getBeltGrade().getLabel())
                        .build()
                : null;

        return MemberResponseDTO.builder()
                .id(m.getId())
                .personalInfo(PersonalInfoResponseDTO.builder()
                        .firstName(pi.getFirstName())
                        .lastName(pi.getLastName())
                        .email(pi.getEmail())
                        .phone(pi.getPhone())
                        .birthDate(pi.getBirthDate())
                        .birthPlace(pi.getBirthPlace())
                        .fiscalCode(pi.getFiscalCode())
                        .gender(pi.getGender())
                        .address(pi.getAddress())
                        .city(pi.getCity())
                        .postalCode(pi.getPostalCode())
                        .province(pi.getProvince())
                        .region(pi.getRegion())
                        .country(pi.getCountry())
                        .beltGrade(beltGradeDTO)
                        .build())
                .registrationDate(m.getRegistrationDate())
                .active(m.getActive())
                .notes(m.getNotes())
                .referredBy(m.getReferredBy() != null
                        ? NamedRefDTO.builder()
                                .id(m.getReferredBy().getId())
                                .name(fullName(m.getReferredBy()))
                                .build()
                        : null)
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
