package com.gymmanager.controller;

import com.gymmanager.dto.FederationCardTypeRequestDTO;
import com.gymmanager.dto.FederationCardTypeResponseDTO;
import com.gymmanager.dto.FederationRequestDTO;
import com.gymmanager.dto.FederationResponseDTO;
import com.gymmanager.service.FederationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Federations and their fee lists. Reading is open to STAFF too — they charge the fees at the
 * desk — but every write is GYM_MANAGER only: prices are the manager's call, not the front
 * desk's. The route-level matcher in SecurityConfig keeps ADMIN/MEMBER/TRAINER out entirely.
 */
@RestController
@RequestMapping("/api/v1/federations")
@RequiredArgsConstructor
public class FederationController {

    private final FederationService federationService;

    @GetMapping
    public List<FederationResponseDTO> findAll() {
        return federationService.findAll();
    }

    @GetMapping("/{id}")
    public FederationResponseDTO findById(@PathVariable Long id) {
        return federationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public FederationResponseDTO create(@Valid @RequestBody FederationRequestDTO dto) {
        return federationService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public FederationResponseDTO update(@PathVariable Long id, @Valid @RequestBody FederationRequestDTO dto) {
        return federationService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public void delete(@PathVariable Long id) {
        federationService.delete(id);
    }

    // ── Fee list ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/card-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public FederationCardTypeResponseDTO addCardType(
            @PathVariable Long id, @Valid @RequestBody FederationCardTypeRequestDTO dto) {
        return federationService.addCardType(id, dto);
    }

    @PutMapping("/card-types/{cardTypeId}")
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public FederationCardTypeResponseDTO updateCardType(
            @PathVariable Long cardTypeId, @Valid @RequestBody FederationCardTypeRequestDTO dto) {
        return federationService.updateCardType(cardTypeId, dto);
    }

    @DeleteMapping("/card-types/{cardTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public void deleteCardType(@PathVariable Long cardTypeId) {
        federationService.deleteCardType(cardTypeId);
    }
}
