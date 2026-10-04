package com.gymmanager.service;

import com.gymmanager.dto.DisciplineRequestDTO;
import com.gymmanager.dto.DisciplineResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.entity.Discipline;
import com.gymmanager.entity.Federation;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.DisciplineRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CRUD for per-gym disciplines. Deliberately NOT built on {@link LookupService}/
 * LookupController: disciplines are per-tenant (scoped to a gym, managed by
 * STAFF/GYM_MANAGER), unlike the global ADMIN-only lookups (roadmap §4 A.1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisciplineService {

    private final DisciplineRepository disciplineRepository;
    private final GymRepository gymRepository;
    private final FederationService federationService;
    private final CurrentUser currentUser;

    @Transactional
    public DisciplineResponseDTO create(DisciplineRequestDTO dto) {
        Gym gym = requireGym();
        String name = dto.getName().trim();
        if (disciplineRepository.existsByGymIdAndNameIgnoreCase(gym.getId(), name)) {
            throw new ConflictException("A discipline with this name already exists: " + name);
        }

        Discipline discipline = Discipline.builder()
                .gym(gym)
                .name(name)
                .federation(resolveFederation(dto.getFederationId()))
                .active(dto.getActive() == null || dto.getActive())
                .build();

        Discipline saved = disciplineRepository.save(discipline);
        log.info("Created discipline id={} name='{}' gymId={}", saved.getId(), saved.getName(), gym.getId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<DisciplineResponseDTO> findAll() {
        Long gymId = requireGym().getId();
        return disciplineRepository.findByGymIdOrderByNameAsc(gymId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public DisciplineResponseDTO findById(Long id) {
        return toDTO(getDiscipline(id));
    }

    @Transactional
    public DisciplineResponseDTO update(Long id, DisciplineRequestDTO dto) {
        Discipline discipline = getDiscipline(id);
        String name = dto.getName().trim();
        if (disciplineRepository.existsByGymIdAndNameIgnoreCaseAndIdNot(discipline.getGym().getId(), name, id)) {
            throw new ConflictException("A discipline with this name already exists: " + name);
        }
        discipline.setName(name);
        discipline.setFederation(resolveFederation(dto.getFederationId()));
        if (dto.getActive() != null) discipline.setActive(dto.getActive());

        log.info("Updated discipline id={}", id);
        return toDTO(disciplineRepository.save(discipline));
    }

    @Transactional
    public void delete(Long id) {
        Discipline discipline = getDiscipline(id);
        disciplineRepository.delete(discipline);
        log.info("Deleted discipline id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Discipline getDiscipline(Long id) {
        Discipline discipline = disciplineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discipline", id));
        assertInScope(discipline);
        return discipline;
    }

    /** Cross-gym access looks like "not found" (anti-IDOR), same pattern as MemberService. */
    private void assertInScope(Discipline discipline) {
        Long gymId = discipline.getGym() != null ? discipline.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Discipline not found");
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

    /** Cross-gym federation looks like not found, same anti-IDOR rule as everywhere else. */
    private Federation resolveFederation(Long federationId) {
        return federationId == null ? null : federationService.getFederation(federationId);
    }

    private DisciplineResponseDTO toDTO(Discipline d) {
        return DisciplineResponseDTO.builder()
                .id(d.getId())
                .name(d.getName())
                .federation(d.getFederation() != null
                        ? NamedRefDTO.builder().id(d.getFederation().getId()).name(d.getFederation().getName()).build()
                        : null)
                .active(d.isActive())
                .build();
    }
}
