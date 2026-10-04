package com.gymmanager.service;

import com.gymmanager.dto.CourseRequestDTO;
import com.gymmanager.dto.CourseResponseDTO;
import com.gymmanager.dto.NamedRefDTO;
import com.gymmanager.dto.TrainerRefDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Course;
import com.gymmanager.entity.Discipline;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.DisciplineRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.CurrentUser;
import com.gymmanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * CRUD for per-gym courses (= the future {@code TrainingClass}, roadmap §4 A.2), on the
 * same scope-gym pattern as {@link com.gymmanager.service.MemberService} — not via
 * LookupService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final DisciplineRepository disciplineRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    /** Roles that may be set as a course's teacher — see {@link #resolveTrainer}. */
    private static final Set<String> TEACHING_ROLES = Set.of(Roles.TRAINER, Roles.GYM_MANAGER);

    @Transactional
    public CourseResponseDTO create(CourseRequestDTO dto) {
        Gym gym = requireGym();
        Discipline discipline = resolveDiscipline(dto.getDisciplineId(), gym.getId());
        AppUser trainer = resolveTrainer(dto.getTrainerId(), gym.getId());

        Course course = Course.builder()
                .gym(gym)
                .discipline(discipline)
                .name(dto.getName().trim())
                .level(dto.getLevel())
                .trainer(trainer)
                .daysOfWeek(resolveDays(dto))
                .startTime(dto.getStartTime())
                .durationMin(dto.getDurationMin())
                .capacity(dto.getCapacity())
                .active(dto.getActive() == null || dto.getActive())
                .build();

        Course saved = courseRepository.save(course);
        log.info("Created course id={} name='{}' gymId={}", saved.getId(), saved.getName(), gym.getId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDTO> findAll() {
        Long gymId = requireGym().getId();
        return courseRepository.findAllForGym(gymId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponseDTO> findToday() {
        Long gymId = requireGym().getId();
        int todayIso = LocalDate.now().getDayOfWeek().getValue();
        return courseRepository.findActiveForGymOnDay(gymId, todayIso)
                .stream().map(this::toDTO).toList();
    }

    /** Active catalog of a gym, read-only — used by the MEMBER /me/courses view. */
    @Transactional(readOnly = true)
    public List<CourseResponseDTO> findActiveForGym(Long gymId) {
        if (gymId == null) return List.of();
        return courseRepository.findActiveForGym(gymId).stream().map(this::toDTO).toList();
    }

    /** Courses taught by the currently authenticated TRAINER — used by /trainer/courses. */
    @Transactional(readOnly = true)
    public List<CourseResponseDTO> findMyCourses() {
        Long trainerId = currentUser.get().userId();
        if (trainerId == null) return List.of();
        return courseRepository.findAllForTrainer(trainerId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponseDTO findById(Long id) {
        return toDTO(getCourse(id));
    }

    /**
     * Resolves a course owned by the current TRAINER (roadmap §4 §C ownership pattern),
     * used by /trainer/courses/{id}/attendance. A course that doesn't exist, belongs to
     * another trainer, or has no trainer assigned all look like "not found" (404, anti-IDOR)
     * — mirrors {@code assertCourseOwnedByCurrentTrainer} from the roadmap analysis.
     */
    @Transactional(readOnly = true)
    public Course getCourseOwnedByCurrentTrainer(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        Long ownerId = course.getTrainer() != null ? course.getTrainer().getId() : null;
        Long currentTrainerId = currentUser.get().userId();
        if (ownerId == null || currentTrainerId == null || !ownerId.equals(currentTrainerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        return course;
    }

    @Transactional
    public CourseResponseDTO update(Long id, CourseRequestDTO dto) {
        Course course = getCourse(id);
        Discipline discipline = resolveDiscipline(dto.getDisciplineId(), course.getGym().getId());
        AppUser trainer = resolveTrainer(dto.getTrainerId(), course.getGym().getId());

        course.setDiscipline(discipline);
        course.setName(dto.getName().trim());
        course.setLevel(dto.getLevel());
        course.setTrainer(trainer);
        // Replace the contents rather than the SortedSet instance: Hibernate tracks the
        // original collection, and swapping it out on a managed entity throws on flush.
        course.getDaysOfWeek().clear();
        course.getDaysOfWeek().addAll(resolveDays(dto));
        course.setStartTime(dto.getStartTime());
        course.setDurationMin(dto.getDurationMin());
        course.setCapacity(dto.getCapacity());
        if (dto.getActive() != null) course.setActive(dto.getActive());

        log.info("Updated course id={}", id);
        return toDTO(courseRepository.save(course));
    }

    @Transactional
    public void delete(Long id) {
        Course course = getCourse(id);
        courseRepository.delete(course);
        log.info("Deleted course id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    Course getCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        assertInScope(course);
        return course;
    }

    /**
     * The days the course runs on, from the new {@code daysOfWeek} list or — for a client not
     * yet updated — the legacy single {@code dayOfWeek}. Folds duplicates and sorts by week
     * order via the TreeSet. The range is re-checked here rather than trusted from the bean
     * validation annotations, so the invariant holds for any caller of the service.
     */
    private static SortedSet<Integer> resolveDays(CourseRequestDTO dto) {
        List<Integer> requested = dto.getDaysOfWeek();
        if (requested == null || requested.isEmpty()) {
            requested = dto.getDayOfWeek() != null ? List.of(dto.getDayOfWeek()) : List.of();
        }
        SortedSet<Integer> days = requested.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        if (days.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A course must run on at least one day of the week");
        }
        if (days.first() < 1 || days.last() > 7) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Days of the week must be between 1 (Monday) and 7 (Sunday)");
        }
        return days;
    }

    private Discipline resolveDiscipline(Long disciplineId, Long gymId) {
        Discipline discipline = disciplineRepository.findById(disciplineId)
                .orElseThrow(() -> new ResourceNotFoundException("Discipline", disciplineId));
        Long disciplineGymId = discipline.getGym() != null ? discipline.getGym().getId() : null;
        if (!gymId.equals(disciplineGymId)) {
            // Cross-gym reference: treat as not found (anti-IDOR), consistent with assertMemberInScope.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Discipline not found");
        }
        return discipline;
    }

    /**
     * Resolves the optional teacher of a course, enforcing it is an AppUser of the same gym
     * whose role can teach: TRAINER, or GYM_MANAGER — in a small gym the manager very often
     * teaches their own courses, and denying that made the feature unusable for single-person
     * gyms. STAFF and MEMBER stay excluded (reception/customer roles), as does ADMIN (no gym).
     * A trainerId pointing to another gym is treated as "not found" (404, IDOR); an existing
     * id whose role cannot teach is a semantic validation error (422), consistent with how
     * MethodArgumentNotValidException surfaces field validation.
     *
     * <p>Note: only a TRAINER gets the {@code /trainer/**} self-service area (it is gated on
     * ROLE_TRAINER in SecurityConfig); a gym manager assigned here manages the course and its
     * attendance through the regular STAFF/GYM_MANAGER screens instead.
     */
    private AppUser resolveTrainer(Long trainerId, Long gymId) {
        if (trainerId == null) return null;
        AppUser trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", trainerId));
        Long trainerGymId = trainer.getGym() != null ? trainer.getGym().getId() : null;
        if (!gymId.equals(trainerGymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainer not found");
        }
        if (!TEACHING_ROLES.contains(trainer.getRole())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "User " + trainerId + " cannot be assigned as a teacher (role " + trainer.getRole() + ")");
        }
        return trainer;
    }

    /** Cross-gym access looks like "not found" (anti-IDOR), same pattern as MemberService. */
    private void assertInScope(Course course) {
        Long gymId = course.getGym() != null ? course.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
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

    CourseResponseDTO toDTO(Course c) {
        return CourseResponseDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .level(c.getLevel())
                .discipline(NamedRefDTO.builder().id(c.getDiscipline().getId()).name(c.getDiscipline().getName()).build())
                .trainer(c.getTrainer() != null
                        ? TrainerRefDTO.builder()
                                .id(c.getTrainer().getId())
                                .fullName(fullName(c.getTrainer()))
                                .build()
                        : null)
                .daysOfWeek(List.copyOf(c.getDaysOfWeek()))
                .dayOfWeek(c.getDaysOfWeek().isEmpty() ? null : c.getDaysOfWeek().first())
                .startTime(c.getStartTime())
                .durationMin(c.getDurationMin())
                .capacity(c.getCapacity())
                .active(c.isActive())
                .build();
    }

    private String fullName(AppUser u) {
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? u.getUsername() : full;
    }
}
