package com.gymmanager.controller;

import com.gymmanager.dto.GymRequestDTO;
import com.gymmanager.dto.GymResponseDTO;
import com.gymmanager.security.CurrentUser;
import com.gymmanager.service.GymService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gyms")
@RequiredArgsConstructor
public class GymController {

    private final GymService gymService;
    private final CurrentUser currentUser;

    // ── Current user's own gym (GYM_MANAGER / STAFF) ───────────────────────
    // Declared before the /{id} routes so the literal "me" path wins.

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('GYM_MANAGER','STAFF')")
    public GymResponseDTO getMyGym() {
        return gymService.get(requireGymId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public GymResponseDTO updateMyGym(@Valid @RequestBody GymRequestDTO dto) {
        return gymService.update(requireGymId(), dto);
    }

    private Long requireGymId() {
        Long gymId = currentUser.gymId();
        if (gymId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        return gymId;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public GymResponseDTO create(@Valid @RequestBody GymRequestDTO dto) {
        return gymService.create(dto);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<GymResponseDTO> list() {
        return gymService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GymResponseDTO get(@PathVariable Long id) {
        return gymService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GymResponseDTO update(@PathVariable Long id, @Valid @RequestBody GymRequestDTO dto) {
        return gymService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        gymService.delete(id);
    }
}
