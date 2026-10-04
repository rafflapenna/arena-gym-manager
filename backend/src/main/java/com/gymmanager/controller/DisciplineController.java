package com.gymmanager.controller;

import com.gymmanager.dto.DisciplineRequestDTO;
import com.gymmanager.dto.DisciplineResponseDTO;
import com.gymmanager.service.DisciplineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roles are gated by {@code SecurityConfig} (hasAnyRole("STAFF","GYM_MANAGER"), ADMIN
 * excluded), matching the convention of MemberController — no per-method @PreAuthorize.
 */
@RestController
@RequestMapping("/api/v1/disciplines")
@RequiredArgsConstructor
public class DisciplineController {

    private final DisciplineService disciplineService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplineResponseDTO create(@Valid @RequestBody DisciplineRequestDTO dto) {
        return disciplineService.create(dto);
    }

    @GetMapping
    public List<DisciplineResponseDTO> findAll() {
        return disciplineService.findAll();
    }

    @GetMapping("/{id}")
    public DisciplineResponseDTO findById(@PathVariable Long id) {
        return disciplineService.findById(id);
    }

    @PutMapping("/{id}")
    public DisciplineResponseDTO update(@PathVariable Long id, @Valid @RequestBody DisciplineRequestDTO dto) {
        return disciplineService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        disciplineService.delete(id);
    }
}
