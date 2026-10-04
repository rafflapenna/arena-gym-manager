package com.gymmanager.service;

import com.gymmanager.dto.BeltGradeRequestDTO;
import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.PaymentMethodRequestDTO;
import com.gymmanager.dto.SubscriptionTypeRequestDTO;
import com.gymmanager.entity.BeltGrade;
import com.gymmanager.entity.PaymentMethod;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.BeltGradeRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.PaymentMethodRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LookupService {

    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusRepository subscriptionStatusRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BeltGradeRepository beltGradeRepository;
    private final GymRepository gymRepository;

    /**
     * Subscription types are per-gym (roadmap: per-gym listino migration). {@code gymId == null}
     * means ADMIN oversight: every row is returned, including the pre-migration legacy rows
     * (gym == null) that a gym-scoped call never sees.
     */
    @Cacheable(value = "lookups", key = "'subscriptionTypes:' + (#gymId != null ? #gymId : 'ALL')")
    public List<LookupDTO> getSubscriptionTypes(Long gymId) {
        List<SubscriptionType> types = gymId != null
                ? subscriptionTypeRepository.findByGymIdOrderByIdAsc(gymId)
                : subscriptionTypeRepository.findAll();
        return types.stream()
                .map(t -> LookupDTO.builder().id(t.getId()).code(t.getCode()).label(t.getLabel()).months(t.getMonths()).defaultPrice(t.getDefaultPrice()).build())
                .toList();
    }

    @CacheEvict(value = "lookups", allEntries = true)
    @Transactional
    public LookupDTO createSubscriptionType(SubscriptionTypeRequestDTO dto, Long gymId) {
        if (gymId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        String code = dto.getCode().toUpperCase();
        if (subscriptionTypeRepository.existsByGymIdAndCode(gymId, code)) {
            throw new ConflictException("Subscription type already exists: " + dto.getCode());
        }
        SubscriptionType saved = subscriptionTypeRepository.save(
                SubscriptionType.builder().code(code).label(dto.getLabel()).months(dto.getMonths())
                        .defaultPrice(dto.getDefaultPrice()).gym(gymRepository.getReferenceById(gymId)).build()
        );
        return LookupDTO.builder().id(saved.getId()).code(saved.getCode()).label(saved.getLabel()).months(saved.getMonths()).defaultPrice(saved.getDefaultPrice()).build();
    }

    /**
     * Updates label, months and the list-price preset of an existing subscription type.
     * {@code code} is immutable by design (it's the stable key referenced elsewhere, e.g. seeded
     * defaults and any external integration): the incoming DTO's {@code code} is ignored rather
     * than rejected, since the request DTO is shared with {@link #createSubscriptionType} and
     * requiring callers to resend the unchanged code as a precondition would be surprising for a
     * "partial update" UI (the backoffice "Listino" page edits label/months/price only).
     */
    @CacheEvict(value = "lookups", allEntries = true)
    @Transactional
    public LookupDTO updateSubscriptionType(Long id, SubscriptionTypeRequestDTO dto, Long gymId) {
        if (gymId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        SubscriptionType existing = subscriptionTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription type", id));
        // Anti-IDOR: a staff/gym-manager may only edit their own gym's types. Legacy global rows
        // (gym == null, pre-migration) are historical FK targets only, not editable by staff.
        if (existing.getGym() == null || !existing.getGym().getId().equals(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription type not found");
        }
        existing.setLabel(dto.getLabel());
        existing.setMonths(dto.getMonths());
        existing.setDefaultPrice(dto.getDefaultPrice());
        SubscriptionType saved = subscriptionTypeRepository.save(existing);
        return LookupDTO.builder().id(saved.getId()).code(saved.getCode()).label(saved.getLabel()).months(saved.getMonths()).defaultPrice(saved.getDefaultPrice()).build();
    }

    /**
     * ADMIN-only. Legacy global rows ({@code gym == null}, pre-migration) are permanently
     * untouchable by design, regardless of usage — they're the historical FK target for
     * subscriptions created before the per-gym listino migration and are never surfaced as
     * editable in the per-gym "Listino" UI, so there's no legitimate flow that would ever
     * need to delete one. A gym-owned type can be deleted only while unused: cascading the
     * delete to its subscriptions (or nulling their FK) would silently rewrite billing
     * history, so a still-referenced type is rejected with 409 instead.
     */
    @CacheEvict(value = "lookups", allEntries = true)
    @Transactional
    public void deleteSubscriptionType(Long id) {
        SubscriptionType type = subscriptionTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription type", id));
        if (type.getGym() == null) {
            throw new ConflictException("Legacy subscription types cannot be deleted");
        }
        long usageCount = subscriptionRepository.countByTypeId(id);
        if (usageCount > 0) {
            throw new ConflictException("Tipo abbonamento ancora usato da " + usageCount + " abbonamenti");
        }
        subscriptionTypeRepository.delete(type);
    }

    @Cacheable(value = "lookups", key = "'subscriptionStatuses'")
    public List<LookupDTO> getSubscriptionStatuses() {
        return subscriptionStatusRepository.findAll().stream()
                .map(s -> LookupDTO.builder().id(s.getId()).code(s.getCode()).label(s.getLabel()).build())
                .toList();
    }

    @Cacheable(value = "lookups", key = "'paymentMethods'")
    public List<LookupDTO> getPaymentMethods() {
        return paymentMethodRepository.findAll().stream()
                .map(m -> LookupDTO.builder().id(m.getId()).code(m.getCode()).label(m.getLabel()).build())
                .toList();
    }

    @CacheEvict(value = "lookups", allEntries = true)
    @Transactional
    public LookupDTO createPaymentMethod(PaymentMethodRequestDTO dto) {
        if (paymentMethodRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ConflictException("Payment method already exists: " + dto.getCode());
        }
        PaymentMethod saved = paymentMethodRepository.save(
                PaymentMethod.builder().code(dto.getCode().toUpperCase()).label(dto.getLabel()).build()
        );
        return LookupDTO.builder().id(saved.getId()).code(saved.getCode()).label(saved.getLabel()).build();
    }

    @Cacheable(value = "lookups", key = "'beltGrades'")
    public List<LookupDTO> getBeltGrades() {
        return beltGradeRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(b -> LookupDTO.builder().id(b.getId()).code(b.getCode()).label(b.getLabel()).build())
                .toList();
    }

    @CacheEvict(value = "lookups", allEntries = true)
    @Transactional
    public LookupDTO createBeltGrade(BeltGradeRequestDTO dto) {
        if (beltGradeRepository.existsByCode(dto.getCode().toUpperCase())) {
            throw new ConflictException("Belt grade already exists: " + dto.getCode());
        }
        int nextOrder = beltGradeRepository.findAllByOrderByDisplayOrderAsc().stream()
                .mapToInt(BeltGrade::getDisplayOrder).max().orElse(0) + 1;
        BeltGrade saved = beltGradeRepository.save(
                BeltGrade.builder()
                        .code(dto.getCode().toUpperCase())
                        .label(dto.getLabel())
                        .displayOrder(nextOrder)
                        .build()
        );
        return LookupDTO.builder().id(saved.getId()).code(saved.getCode()).label(saved.getLabel()).build();
    }
}
