package com.gymmanager.controller;

import com.gymmanager.dto.MeLeadRequestDTO;
import com.gymmanager.dto.MeLeadResponseDTO;
import com.gymmanager.service.MeLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Prove inserite dai soci" (2026-10-01): a MEMBER reporting a friend's free trial. Role is
 * gated by {@code SecurityConfig}'s existing {@code /api/v1/me/**} -> hasRole("MEMBER")
 * matcher, same convention as {@link MeController} — no per-method @PreAuthorize needed here.
 */
@RestController
@RequestMapping("/api/v1/me/leads")
@RequiredArgsConstructor
public class MeLeadController {

    private final MeLeadService meLeadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeLeadResponseDTO create(@Valid @RequestBody MeLeadRequestDTO dto) {
        return meLeadService.create(dto);
    }

    @GetMapping
    public List<MeLeadResponseDTO> findMine() {
        return meLeadService.findMine();
    }
}
