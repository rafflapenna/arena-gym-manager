package com.gymmanager.service;

import com.gymmanager.dto.FederationCardTypeRequestDTO;
import com.gymmanager.dto.FederationCardTypeResponseDTO;
import com.gymmanager.dto.FederationRequestDTO;
import com.gymmanager.dto.FederationResponseDTO;
import com.gymmanager.entity.Federation;
import com.gymmanager.entity.FederationCardType;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.FederationCardTypeRepository;
import com.gymmanager.repository.FederationRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberFederationCardRepository;
import com.gymmanager.repository.MemberFeeRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-gym federations and their fee lists. Reading is open to STAFF (they charge the fees);
 * writing is restricted to GYM_MANAGER at the route level, since prices are the manager's call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FederationService {

    private final FederationRepository federationRepository;
    private final FederationCardTypeRepository cardTypeRepository;
    private final MemberFeeRepository memberFeeRepository;
    private final MemberFederationCardRepository memberFederationCardRepository;
    private final GymRepository gymRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<FederationResponseDTO> findAll() {
        Long gymId = requireGym().getId();
        return federationRepository.findByGymIdOrderByNameAsc(gymId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public FederationResponseDTO findById(Long id) {
        return toDTO(getFederation(id));
    }

    @Transactional
    public FederationResponseDTO create(FederationRequestDTO dto) {
        Gym gym = requireGym();
        String name = dto.getName().trim();
        if (federationRepository.existsByGymIdAndNameIgnoreCase(gym.getId(), name)) {
            throw new ConflictException("A federation with this name already exists: " + name);
        }
        Federation saved = federationRepository.save(Federation.builder()
                .gym(gym)
                .name(name)
                .seasonStartMonth(dto.getSeasonStartMonth())
                .active(dto.getActive() == null || dto.getActive())
                .build());
        log.info("Created federation id={} '{}' gymId={}", saved.getId(), name, gym.getId());
        return toDTO(saved);
    }

    @Transactional
    public FederationResponseDTO update(Long id, FederationRequestDTO dto) {
        Federation federation = getFederation(id);
        String name = dto.getName().trim();
        if (federationRepository.existsByGymIdAndNameIgnoreCaseAndIdNot(
                federation.getGym().getId(), name, id)) {
            throw new ConflictException("A federation with this name already exists: " + name);
        }
        federation.setName(name);
        federation.setSeasonStartMonth(dto.getSeasonStartMonth());
        if (dto.getActive() != null) federation.setActive(dto.getActive());
        return toDTO(federationRepository.save(federation));
    }

    @Transactional
    public void delete(Long id) {
        Federation federation = getFederation(id);
        // Fees snapshot their amount but keep a FK to the federation as history; deleting it
        // would either break that link or erase past receipts. Deactivating hides it from the
        // pickers without touching what has already been charged.
        if (!cardTypeRepository.findByFederationIdOrderByPriceAsc(id).isEmpty()) {
            throw new ConflictException(
                    "This federation still has card types. Delete them first, or deactivate the federation.");
        }
        if (memberFederationCardRepository.existsByFederationId(id)) {
            throw new ConflictException(
                    "Some members have a card number with this federation. Deactivate it instead.");
        }
        federationRepository.delete(federation);
        log.info("Deleted federation id={}", id);
    }

    // ── Card types (the fee list) ─────────────────────────────────────────

    @Transactional
    public FederationCardTypeResponseDTO addCardType(Long federationId, FederationCardTypeRequestDTO dto) {
        Federation federation = getFederation(federationId);
        String name = dto.getName().trim();
        if (cardTypeRepository.existsByFederationIdAndNameIgnoreCase(federationId, name)) {
            throw new ConflictException("A card type with this name already exists: " + name);
        }
        FederationCardType saved = cardTypeRepository.save(FederationCardType.builder()
                .federation(federation)
                .name(name)
                .price(dto.getPrice())
                .active(dto.getActive() == null || dto.getActive())
                .build());
        return toDTO(saved);
    }

    @Transactional
    public FederationCardTypeResponseDTO updateCardType(Long cardTypeId, FederationCardTypeRequestDTO dto) {
        FederationCardType cardType = getCardType(cardTypeId);
        cardType.setName(dto.getName().trim());
        cardType.setPrice(dto.getPrice());
        if (dto.getActive() != null) cardType.setActive(dto.getActive());
        return toDTO(cardTypeRepository.save(cardType));
    }

    @Transactional
    public void deleteCardType(Long cardTypeId) {
        FederationCardType cardType = getCardType(cardTypeId);
        // A charged fee points at the line it was charged on, as history. Removing the line
        // would rewrite that, so it is refused: deactivating keeps it out of the pickers.
        if (memberFeeRepository.existsByCardTypeId(cardTypeId)) {
            throw new ConflictException(
                    "This card type has already been charged and cannot be deleted. Deactivate it instead.");
        }
        cardTypeRepository.delete(cardType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Resolves a card type and checks it belongs to the caller's gym (404 otherwise).
     *
     * <p>Transactional because the scope check walks the LAZY federation proxy: called from
     * another service's transaction it would join that one, but called on its own it needs a
     * session of its own or the proxy can't initialise.
     */
    @Transactional(readOnly = true)
    public FederationCardType getCardType(Long id) {
        FederationCardType cardType = cardTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FederationCardType", id));
        assertInScope(cardType.getFederation());
        return cardType;
    }

    @Transactional(readOnly = true)
    public Federation getFederation(Long id) {
        Federation federation = federationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Federation", id));
        assertInScope(federation);
        return federation;
    }

    private void assertInScope(Federation federation) {
        Long gymId = federation.getGym() != null ? federation.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Federation not found");
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

    private FederationResponseDTO toDTO(Federation f) {
        LocalDate today = LocalDate.now();
        return FederationResponseDTO.builder()
                .id(f.getId())
                .name(f.getName())
                .seasonStartMonth(f.getSeasonStartMonth())
                .active(f.isActive())
                .currentSeasonStart(f.seasonStart(today))
                .currentSeasonEnd(f.seasonEnd(today))
                .cardTypes(cardTypeRepository.findByFederationIdOrderByPriceAsc(f.getId())
                        .stream().map(this::toDTO).toList())
                .build();
    }

    private FederationCardTypeResponseDTO toDTO(FederationCardType c) {
        return FederationCardTypeResponseDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .price(c.getPrice())
                .active(c.isActive())
                .build();
    }
}
