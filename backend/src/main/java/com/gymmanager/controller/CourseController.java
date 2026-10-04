package com.gymmanager.controller;

import com.gymmanager.dto.CourseRequestDTO;
import com.gymmanager.dto.CourseResponseDTO;
import com.gymmanager.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roles are gated by {@code SecurityConfig} (hasAnyRole("STAFF","GYM_MANAGER"), ADMIN
 * excluded) — no per-method @PreAuthorize, same convention as MemberController.
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponseDTO create(@Valid @RequestBody CourseRequestDTO dto) {
        return courseService.create(dto);
    }

    @GetMapping
    public List<CourseResponseDTO> findAll() {
        return courseService.findAll();
    }

    // Declared before /{id} so the literal "today" path wins (same convention as GymController /me).
    @GetMapping("/today")
    public List<CourseResponseDTO> findToday() {
        return courseService.findToday();
    }

    @GetMapping("/{id}")
    public CourseResponseDTO findById(@PathVariable Long id) {
        return courseService.findById(id);
    }

    @PutMapping("/{id}")
    public CourseResponseDTO update(@PathVariable Long id, @Valid @RequestBody CourseRequestDTO dto) {
        return courseService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        courseService.delete(id);
    }
}
