package com.gymmanager.controller;

import com.gymmanager.dto.LeadConfirmRequestDTO;
import com.gymmanager.dto.LeadConversionResponseDTO;
import com.gymmanager.dto.LeadConvertRequestDTO;
import com.gymmanager.dto.LeadRequestDTO;
import com.gymmanager.dto.LeadResponseDTO;
import com.gymmanager.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roles are gated by {@code SecurityConfig} (hasAnyRole("STAFF","GYM_MANAGER"), ADMIN
 * excluded) — no per-method @PreAuthorize, same convention as CourseController.
 */
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponseDTO create(@Valid @RequestBody LeadRequestDTO dto) {
        return leadService.create(dto);
    }

    // status/origin are bound as plain Strings, not their enums: an invalid value on a
    // @RequestParam enum throws MethodArgumentTypeMismatchException, which GlobalExceptionHandler
    // does NOT handle -> falls through to the Exception.class catch-all -> 500. Parsing them
    // in LeadService instead lets an invalid value raise a ResponseStatusException(BAD_REQUEST),
    // which IS handled -> a clean 400.
    @GetMapping
    public List<LeadResponseDTO> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String origin
    ) {
        return leadService.findAll(status, q, origin);
    }

    @GetMapping("/{id}")
    public LeadResponseDTO findById(@PathVariable Long id) {
        return leadService.findById(id);
    }

    @PutMapping("/{id}")
    public LeadResponseDTO update(@PathVariable Long id, @Valid @RequestBody LeadRequestDTO dto) {
        return leadService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        leadService.delete(id);
    }

    @PostMapping("/{id}/convert")
    public LeadConversionResponseDTO convert(@PathVariable Long id, @Valid @RequestBody LeadConvertRequestDTO dto) {
        return leadService.convert(id, dto);
    }

    /** Reception confirms a self-reported trial ("prove inserite dai soci"), picking the trial date. */
    @PostMapping("/{id}/confirm")
    public LeadResponseDTO confirm(@PathVariable Long id, @Valid @RequestBody LeadConfirmRequestDTO dto) {
        return leadService.confirm(id, dto);
    }

    /** Reception declines a self-reported trial ("prove inserite dai soci"). */
    @PostMapping("/{id}/decline")
    public LeadResponseDTO decline(@PathVariable Long id) {
        return leadService.decline(id);
    }
}
