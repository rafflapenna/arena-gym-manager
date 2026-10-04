package com.gymmanager.service;

import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.SubscriptionPackageRefDTO;
import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.dto.SubscriptionResponseDTO;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.ReferralReward;
import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.entity.TrainingPackage;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PackageRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final SubscriptionStatusRepository subscriptionStatusRepository;
    private final MemberRepository memberRepository;
    private final ReferralRewardService referralRewardService;
    private final PackageRepository packageRepository;
    private final CurrentUser currentUser;
    private final LookupService lookupService;

    private void assertMemberInScope(Member member) {
        Long gymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    @Transactional
    public SubscriptionResponseDTO create(Long memberId, SubscriptionRequestDTO dto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        assertMemberInScope(member);

        boolean hasType = dto.getTypeId() != null;
        boolean hasPackage = dto.getPackageId() != null;
        if (hasType == hasPackage) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Exactly one of typeId or packageId must be provided");
        }

        SubscriptionStatus activeStatus = subscriptionStatusRepository.findByCode("ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Status ACTIVE not found"));

        // Checked up front so an invalid/spent discount rejects the request before anything
        // is written; it is burned onto the subscription further down, once it has an id.
        ReferralReward reward = referralRewardService.validateForRedemption(dto.getRewardId(), member);

        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .member(member)
                .startDate(dto.getStartDate())
                .status(activeStatus)
                .notes(dto.getNotes());

        if (hasType) {
            if (dto.getPrice() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "price is required for a type-based subscription");
            }
            SubscriptionType type = subscriptionTypeRepository.findById(dto.getTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription type not found with id: " + dto.getTypeId()));
            // Anti-IDOR: a per-gym type must belong to the member's gym. Legacy global rows
            // (gym == null, pre-migration) stay accepted as a grace for stale FE caches.
            Long typeGymId = type.getGym() != null ? type.getGym().getId() : null;
            Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
            if (typeGymId != null && !typeGymId.equals(memberGymId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription type not found");
            }
            builder.type(type)
                    .endDate(dto.getStartDate().plusMonths(type.getMonths()))
                    .price(referralRewardService.applyDiscount(dto.getPrice(), reward));
        } else {
            TrainingPackage pkg = packageRepository.findById(dto.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Package", dto.getPackageId()));
            assertPackageInScope(pkg, member);
            // Price is auto-populated from the package and frozen as a snapshot: later
            // changes to the package's list price must not rewrite past subscriptions.
            // purchasePrice stays the undiscounted list price: it is the historical snapshot,
            // so together with price it also records what the referral discount took off.
            builder.trainingPackage(pkg)
                    .endDate(dto.getStartDate().plusMonths(pkg.getDurationMonths()))
                    .price(referralRewardService.applyDiscount(pkg.getPrice(), reward))
                    .purchasePrice(pkg.getPrice());
        }

        Subscription saved = subscriptionRepository.save(builder.build());
        referralRewardService.markUsed(reward, saved);
        // Same transaction: selling a subscription is what turns "brought a friend" into a
        // real discount for whoever brought this member in.
        referralRewardService.grantForSubscription(member, saved);

        log.info("Created subscription id={} for memberId={}", saved.getId(), memberId);
        return toDTO(saved);
    }

    /** A package must belong to the same gym as the member it's being sold to (anti-IDOR, 404). */
    private void assertPackageInScope(TrainingPackage pkg, Member member) {
        Long packageGymId = pkg.getGym() != null ? pkg.getGym().getId() : null;
        Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
        if (packageGymId == null || !packageGymId.equals(memberGymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
        }
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponseDTO> findByMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        assertMemberInScope(member);
        return subscriptionRepository.findByMemberIdOrderByStartDateDesc(memberId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponseDTO> findAll(String statusCode) {
        // Resolve gym scope: null means ADMIN (all gyms), otherwise restrict to own gym (same
        // pattern as findExpiring). Pushed into the query's WHERE clause instead of loading every
        // gym's subscriptions into memory and filtering them in Java.
        Long gymId = currentUser.isAdmin() ? null : currentUser.gymId();
        List<Subscription> all;
        if (statusCode != null) {
            SubscriptionStatus status = subscriptionStatusRepository.findByCode(statusCode.toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Status not found: " + statusCode));
            all = subscriptionRepository.findByStatusOrderByEndDateAsc(status, gymId);
        } else {
            all = subscriptionRepository.findAllWithDetails(gymId);
        }
        return all.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponseDTO> findExpiring(int days) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(days);
        // Resolve gym scope: null means ADMIN (all gyms), otherwise restrict to own gym.
        Long gymId = currentUser.isAdmin() ? null : currentUser.gymId();
        // Resolve the ACTIVE status id from the cached lookup list — zero extra DB queries.
        Long activeStatusId = lookupService.getSubscriptionStatuses().stream()
                .filter(s -> "ACTIVE".equals(s.getCode()))
                .map(LookupDTO::getId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Status ACTIVE not found"));
        return subscriptionRepository.findExpiringSoon(today, threshold, activeStatusId, gymId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public SubscriptionResponseDTO cancel(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));
        assertMemberInScope(subscription.getMember());

        SubscriptionStatus cancelledStatus = subscriptionStatusRepository.findByCode("CANCELLED")
                .orElseThrow(() -> new ResourceNotFoundException("Status CANCELLED not found"));

        subscription.setStatus(cancelledStatus);
        log.info("Cancelled subscription id={}", id);
        return toDTO(subscriptionRepository.save(subscription));
    }

    private SubscriptionResponseDTO toDTO(Subscription s) {
        // s.getType() is null for package-based subscriptions (roadmap §4 A.4) — the
        // fragile point flagged in the analysis: dereferencing it unconditionally here
        // would NPE on every listing that includes one.
        return SubscriptionResponseDTO.builder()
                .id(s.getId())
                .memberId(s.getMember().getId())
                .memberFullName(s.getMember().getPersonalInfo().getFirstName() + " " + s.getMember().getPersonalInfo().getLastName())
                .type(s.getType() != null
                        ? LookupDTO.builder()
                                .id(s.getType().getId())
                                .code(s.getType().getCode())
                                .label(s.getType().getLabel())
                                .months(s.getType().getMonths())
                                .build()
                        : null)
                .trainingPackage(s.getTrainingPackage() != null ? toPackageRefDTO(s.getTrainingPackage()) : null)
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .price(s.getPrice())
                .purchasePrice(s.getPurchasePrice())
                .status(LookupDTO.builder()
                        .id(s.getStatus().getId())
                        .code(s.getStatus().getCode())
                        .label(s.getStatus().getLabel())
                        .build())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private SubscriptionPackageRefDTO toPackageRefDTO(TrainingPackage p) {
        return SubscriptionPackageRefDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .durationMonths(p.getDurationMonths())
                .courses(p.getCourses().stream()
                        .map(c -> NamedRefDTO.builder().id(c.getId()).name(c.getName()).build())
                        .toList())
                .build();
    }
}
