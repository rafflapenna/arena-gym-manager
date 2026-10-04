package com.gymmanager.service;

import com.gymmanager.dto.GymRequestDTO;
import com.gymmanager.dto.GymResponseDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.FederationCardTypeRepository;
import com.gymmanager.repository.FederationRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GymService {

    private final GymRepository gymRepository;
    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final FederationRepository federationRepository;
    private final FederationCardTypeRepository cardTypeRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionTypeSeeder subscriptionTypeSeeder;
    private final FederationSeeder federationSeeder;

    @Transactional
    public GymResponseDTO create(GymRequestDTO dto) {
        Gym gym = Gym.builder()
                .name(dto.name().trim())
                .address(dto.address())
                .city(dto.city())
                .postalCode(dto.postalCode())
                .province(dto.province())
                .region(dto.region())
                .phone(dto.phone())
                .vatNumber(dto.vatNumber())
                .contactEmail(StringUtils.hasText(dto.contactEmail())
                        ? dto.contactEmail().toLowerCase().trim() : null)
                .build();
        Gym saved = gymRepository.save(gym);
        // New gym starts with a base price list, not an empty one.
        subscriptionTypeSeeder.ensureDefaultsForGym(saved);
        federationSeeder.ensureDefaultsForGym(saved);
        log.info("Created gym '{}' (id={})", saved.getName(), saved.getId());
        return toDTO(saved);
    }

    public List<GymResponseDTO> list() {
        // potential N+1, acceptable for low gym count
        return gymRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public GymResponseDTO get(Long id) {
        Gym gym = gymRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gym", id));
        return toDTO(gym);
    }

    @Transactional
    public GymResponseDTO update(Long id, GymRequestDTO dto) {
        Gym gym = gymRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gym", id));
        gym.setName(dto.name().trim());
        gym.setAddress(dto.address());
        gym.setCity(dto.city());
        gym.setPostalCode(dto.postalCode());
        gym.setProvince(dto.province());
        gym.setRegion(dto.region());
        gym.setPhone(dto.phone());
        gym.setVatNumber(dto.vatNumber());
        // Null significa "lascia com'e'": il form della palestra puo' non mandare il campo,
        // e un null non deve azzerare la quota facendo fallire il NOT NULL in colonna.
        if (dto.membershipFeeAmount() != null) gym.setMembershipFeeAmount(dto.membershipFeeAmount());
        gym.setContactEmail(StringUtils.hasText(dto.contactEmail())
                ? dto.contactEmail().toLowerCase().trim() : null);
        return toDTO(gymRepository.save(gym));
    }

    @Transactional
    public void delete(Long id) {
        gymRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gym", id));
        if (userRepository.countByGymId(id) > 0 || memberRepository.countByGymId(id) > 0) {
            throw new ConflictException("Cannot delete gym: it still has linked users or members");
        }
        // Listino e federazioni sono configurazione di proprieta' della palestra, seminata
        // alla sua creazione: senza rimuoverle la FK blocca la cancellazione di QUALUNQUE
        // palestra, perche' ognuna nasce con le sue. Il controllo sopra garantisce che non ci
        // siano ne' soci ne' utenti, quindi nemmeno abbonamenti che puntino a questi tipi.
        // I tipi legacy globali (gym_id IS NULL) non sono toccati: findByGymId ritorna solo
        // le righe di questa palestra.
        subscriptionTypeRepository.deleteAll(subscriptionTypeRepository.findByGymIdOrderByIdAsc(id));

        federationRepository.findByGymIdOrderByNameAsc(id).forEach(f -> {
            cardTypeRepository.deleteAll(cardTypeRepository.findByFederationIdOrderByPriceAsc(f.getId()));
            federationRepository.delete(f);
        });

        gymRepository.deleteById(id);
        log.info("Deleted gym id={}", id);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private GymResponseDTO toDTO(Gym gym) {
        Long gymId = gym.getId();

        // potential N+1, acceptable for low gym count
        List<AppUser> instructors = userRepository.findByGymIdAndRole(gymId, "GYM_MANAGER");
        AppUser instructor = instructors.isEmpty() ? null : instructors.get(0);

        long staffCount = userRepository.findByGymIdAndRole(gymId, "STAFF").size();
        long memberCount = memberRepository.countByGymId(gymId);

        return GymResponseDTO.builder()
                .id(gymId)
                .name(gym.getName())
                .address(gym.getAddress())
                .city(gym.getCity())
                .postalCode(gym.getPostalCode())
                .province(gym.getProvince())
                .region(gym.getRegion())
                .phone(gym.getPhone())
                .vatNumber(gym.getVatNumber())
                .membershipFeeAmount(gym.getMembershipFeeAmount())
                .contactEmail(gym.getContactEmail())
                .createdAt(gym.getCreatedAt())
                .instructorId(instructor != null ? instructor.getId() : null)
                .instructorEmail(instructor != null ? instructor.getUsername() : null)
                .staffCount(staffCount)
                .memberCount(memberCount)
                .build();
    }
}
