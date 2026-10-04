package com.gymmanager.service;

import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.PackageRequestDTO;
import com.gymmanager.dto.PackageResponseDTO;
import com.gymmanager.entity.Course;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.TrainingPackage;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.PackageRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * CRUD for per-gym training packages (roadmap §4 A.3), on the same scope-gym pattern
 * as {@link CourseService}/{@link DisciplineService} — not via LookupService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PackageService {

    private final PackageRepository packageRepository;
    private final CourseRepository courseRepository;
    private final GymRepository gymRepository;
    private final CurrentUser currentUser;

    @Transactional
    public PackageResponseDTO create(PackageRequestDTO dto) {
        Gym gym = requireGym();
        List<Course> courses = resolveCourses(dto.getCourseIds(), gym.getId());

        TrainingPackage pkg = TrainingPackage.builder()
                .gym(gym)
                .name(dto.getName().trim())
                .price(dto.getPrice())
                .durationMonths(dto.getDurationMonths())
                .active(dto.getActive() == null || dto.getActive())
                .courses(courses)
                .build();

        TrainingPackage saved = packageRepository.save(pkg);
        log.info("Created package id={} name='{}' gymId={}", saved.getId(), saved.getName(), gym.getId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<PackageResponseDTO> findAll() {
        Long gymId = requireGym().getId();
        return packageRepository.findByGymIdOrderByNameAsc(gymId).stream().map(this::toDTO).toList();
    }

    /** Active catalog of a gym, read-only — used by the MEMBER /me/packages view. */
    @Transactional(readOnly = true)
    public List<PackageResponseDTO> findActiveForGym(Long gymId) {
        if (gymId == null) return List.of();
        return packageRepository.findByGymIdAndActiveTrueOrderByNameAsc(gymId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PackageResponseDTO findById(Long id) {
        return toDTO(getPackage(id));
    }

    @Transactional
    public PackageResponseDTO update(Long id, PackageRequestDTO dto) {
        TrainingPackage pkg = getPackage(id);
        List<Course> courses = resolveCourses(dto.getCourseIds(), pkg.getGym().getId());

        pkg.setName(dto.getName().trim());
        pkg.setPrice(dto.getPrice());
        pkg.setDurationMonths(dto.getDurationMonths());
        if (dto.getActive() != null) pkg.setActive(dto.getActive());
        pkg.setCourses(courses);

        log.info("Updated package id={}", id);
        return toDTO(packageRepository.save(pkg));
    }

    @Transactional
    public void delete(Long id) {
        TrainingPackage pkg = getPackage(id);
        packageRepository.delete(pkg);
        log.info("Deleted package id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    TrainingPackage getPackage(Long id) {
        TrainingPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
        assertInScope(pkg);
        return pkg;
    }

    /** Resolves the course ids, enforcing every one belongs to the given gym (anti-IDOR, 404). */
    private List<Course> resolveCourses(List<Long> courseIds, Long gymId) {
        List<Long> distinctIds = courseIds.stream().distinct().toList();
        List<Course> courses = courseRepository.findAllById(distinctIds);
        if (courses.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more courses were not found");
        }
        for (Course course : courses) {
            Long courseGymId = course.getGym() != null ? course.getGym().getId() : null;
            if (!gymId.equals(courseGymId)) {
                // Cross-gym reference: treat as not found (anti-IDOR), consistent with CourseService.
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
            }
        }
        return courses;
    }

    /** Cross-gym access looks like "not found" (anti-IDOR), same pattern as MemberService. */
    private void assertInScope(TrainingPackage pkg) {
        Long gymId = pkg.getGym() != null ? pkg.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
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

    PackageResponseDTO toDTO(TrainingPackage p) {
        return PackageResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .durationMonths(p.getDurationMonths())
                .active(p.isActive())
                .courses(p.getCourses().stream()
                        .map(c -> NamedRefDTO.builder().id(c.getId()).name(c.getName()).build())
                        .toList())
                .build();
    }
}
