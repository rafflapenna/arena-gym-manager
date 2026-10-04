package com.gymmanager.service;

import com.gymmanager.dto.MemberFederationCardRequestDTO;
import com.gymmanager.dto.MemberFederationCardResponseDTO;
import com.gymmanager.dto.MemberFeeRequestDTO;
import com.gymmanager.dto.MemberFeeResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.entity.Federation;
import com.gymmanager.entity.FederationCardType;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MemberFederationCard;
import com.gymmanager.entity.MemberFee;
import com.gymmanager.entity.MemberFeeType;
import com.gymmanager.entity.PaymentMethod;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberFederationCardRepository;
import com.gymmanager.repository.MemberFeeRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PaymentMethodRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Charging and listing the two recurring fees a member owes besides their subscription: the
 * gym's yearly membership fee and federation registration.
 *
 * <p>The two expire on different rules, deliberately — see {@link #computeValidUntil}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberFeeService {

    private final MemberFeeRepository memberFeeRepository;
    private final MemberFederationCardRepository cardRepository;
    private final MemberRepository memberRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final FederationService federationService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<MemberFeeResponseDTO> findByMember(Long memberId) {
        getMember(memberId);
        return memberFeeRepository.findByMemberIdOrderByValidUntilDesc(memberId)
                .stream().map(f -> toDTO(f, false)).toList();
    }

    /** Fees expiring between today and {@code days} ahead — drives the renewal reminders. */
    @Transactional(readOnly = true)
    public List<MemberFeeResponseDTO> findExpiring(int days) {
        Long gymId = currentUser.gymId();
        if (gymId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        LocalDate today = LocalDate.now();
        return memberFeeRepository.findExpiring(gymId, today, today.plusDays(days))
                .stream().map(f -> toDTO(f, true)).toList();
    }

    @Transactional
    public MemberFeeResponseDTO charge(Long memberId, MemberFeeRequestDTO dto) {
        Member member = getMember(memberId);
        MemberFeeType type = parseType(dto.getType());
        LocalDate paidOn = dto.getPaidOn() != null ? dto.getPaidOn() : LocalDate.now();

        Federation federation = null;
        FederationCardType cardType = null;
        BigDecimal amount;

        if (type == MemberFeeType.FEDERATION) {
            if (dto.getCardTypeId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "cardTypeId is required for a federation fee");
            }
            cardType = federationService.getCardType(dto.getCardTypeId());
            federation = cardType.getFederation();
            amount = dto.getAmount() != null ? dto.getAmount() : cardType.getPrice();
        } else {
            // The gym's own fee: the amount comes from the gym's current setting, which the
            // manager changes at will. Snapshotted below so a later change never rewrites this.
            BigDecimal configured = member.getGym() != null ? member.getGym().getMembershipFeeAmount() : null;
            amount = dto.getAmount() != null ? dto.getAmount() : configured;
            if (amount == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No membership fee amount configured for this gym");
            }
        }

        PaymentMethod paymentMethod = dto.getPaymentMethodId() == null ? null
                : paymentMethodRepository.findById(dto.getPaymentMethodId())
                        .orElseThrow(() -> new ResourceNotFoundException("PaymentMethod", dto.getPaymentMethodId()));

        MemberFee fee = memberFeeRepository.save(MemberFee.builder()
                .member(member)
                .type(type)
                .federation(federation)
                .cardType(cardType)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .paidOn(paidOn)
                .validUntil(computeValidUntil(type, federation, paidOn))
                .notes(dto.getNotes())
                .build());

        log.info("Charged {} fee id={} amount={} to memberId={} valid until {}",
                type, fee.getId(), amount, memberId, fee.getValidUntil());
        return toDTO(fee, false);
    }

    @Transactional
    public void delete(Long feeId) {
        MemberFee fee = memberFeeRepository.findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException("MemberFee", feeId));
        getMember(fee.getMember().getId()); // scope check
        memberFeeRepository.delete(fee);
        log.info("Deleted member fee id={}", feeId);
    }

    // ── Card numbers (one per federation, never changes) ──────────────────

    @Transactional(readOnly = true)
    public List<MemberFederationCardResponseDTO> findCards(Long memberId) {
        getMember(memberId);
        return cardRepository.findByMemberIdOrderByFederationNameAsc(memberId)
                .stream().map(MemberFeeService::toCardDTO).toList();
    }

    /** Sets (or corrects) the member's card number with a federation of their gym. */
    @Transactional
    public MemberFederationCardResponseDTO saveCard(Long memberId, Long federationId,
                                                    MemberFederationCardRequestDTO dto) {
        Member member = getMember(memberId);
        Federation federation = federationService.getFederation(federationId);
        String number = dto.getCardNumber().trim();
        if (cardRepository.existsByFederationIdAndCardNumberAndMemberIdNot(federationId, number, memberId)) {
            throw new ConflictException("Card number " + number + " already belongs to another member",
                    "FEDERATION_CARD_DUPLICATE");
        }
        MemberFederationCard card = cardRepository.findByMemberIdAndFederationId(memberId, federationId)
                .orElseGet(() -> MemberFederationCard.builder().member(member).federation(federation).build());
        card.setCardNumber(number);
        MemberFederationCard saved = cardRepository.save(card);
        log.info("Set federation card memberId={} federationId={}", memberId, federationId);
        return toCardDTO(saved);
    }

    @Transactional
    public void deleteCard(Long memberId, Long federationId) {
        getMember(memberId);
        cardRepository.findByMemberIdAndFederationId(memberId, federationId)
                .ifPresent(cardRepository::delete);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * The two fees expire on genuinely different rules, and getting this wrong shows up as
     * wrong renewal dates months later:
     *
     * <ul>
     *   <li><b>Association</b>: 12 months from the day it was paid, so every member carries
     *       their own date.</li>
     *   <li><b>Federation</b>: the federation's season, which is fixed and shared. FederKombat
     *       runs 1 September → 31 August, FIJLKAM expires 31 December. Paying mid-season buys
     *       the rest of that season, not twelve months.</li>
     * </ul>
     */
    private static LocalDate computeValidUntil(MemberFeeType type, Federation federation, LocalDate paidOn) {
        if (type == MemberFeeType.ASSOCIATION) {
            return paidOn.plusYears(1).minusDays(1);
        }
        return federation.seasonEnd(paidOn);
    }

    private static MemberFeeType parseType(String type) {
        try {
            return MemberFeeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fee type: " + type);
        }
    }

    private Member getMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", id));
        if (!currentUser.isAdmin()) {
            Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
            if (memberGymId == null || !memberGymId.equals(currentUser.gymId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
            }
        }
        return member;
    }

    private MemberFeeResponseDTO toDTO(MemberFee f, boolean includeMember) {
        return MemberFeeResponseDTO.builder()
                .id(f.getId())
                .type(f.getType().name())
                .federation(ref(f.getFederation() != null ? f.getFederation().getId() : null,
                        f.getFederation() != null ? f.getFederation().getName() : null))
                .cardType(ref(f.getCardType() != null ? f.getCardType().getId() : null,
                        f.getCardType() != null ? f.getCardType().getName() : null))
                .amount(f.getAmount())
                .paymentMethod(ref(f.getPaymentMethod() != null ? f.getPaymentMethod().getId() : null,
                        f.getPaymentMethod() != null ? f.getPaymentMethod().getLabel() : null))
                .paidOn(f.getPaidOn())
                .validUntil(f.getValidUntil())
                .expired(f.getValidUntil().isBefore(LocalDate.now()))
                .notes(f.getNotes())
                .member(includeMember
                        ? ref(f.getMember().getId(), MemberService.fullName(f.getMember()))
                        : null)
                .build();
    }

    private static MemberFederationCardResponseDTO toCardDTO(MemberFederationCard c) {
        return MemberFederationCardResponseDTO.builder()
                .federation(ref(c.getFederation().getId(), c.getFederation().getName()))
                .cardNumber(c.getCardNumber())
                .build();
    }

    private static NamedRefDTO ref(Long id, String name) {
        return id == null ? null : NamedRefDTO.builder().id(id).name(name).build();
    }
}
