package com.gymmanager.controller;

import com.gymmanager.dto.PackageRequestDTO;
import com.gymmanager.dto.PackageResponseDTO;
import com.gymmanager.service.PackageService;
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
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PackageResponseDTO create(@Valid @RequestBody PackageRequestDTO dto) {
        return packageService.create(dto);
    }

    @GetMapping
    public List<PackageResponseDTO> findAll() {
        return packageService.findAll();
    }

    @GetMapping("/{id}")
    public PackageResponseDTO findById(@PathVariable Long id) {
        return packageService.findById(id);
    }

    @PutMapping("/{id}")
    public PackageResponseDTO update(@PathVariable Long id, @Valid @RequestBody PackageRequestDTO dto) {
        return packageService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        packageService.delete(id);
    }
}
