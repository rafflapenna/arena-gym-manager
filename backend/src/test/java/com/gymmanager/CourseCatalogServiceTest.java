package com.gymmanager;

import com.gymmanager.dto.CourseRequestDTO;
import com.gymmanager.dto.CourseResponseDTO;
import com.gymmanager.dto.DisciplineRequestDTO;
import com.gymmanager.dto.DisciplineResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PackageRequestDTO;
import com.gymmanager.dto.PackageResponseDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.dto.SubscriptionResponseDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.DisciplineRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PackageRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.CourseService;
import com.gymmanager.service.DisciplineService;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.PackageService;
import com.gymmanager.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for the Corsi & Pacchetti feature (roadmap §4): multi-gym scoping,
 * discipline uniqueness, trainer validation, package course scoping, and the
 * Subscription->Package integration (XOR validation, price snapshot, no NPE on toDTO).
 *
 * Route-level 403 checks (ADMIN/MEMBER/TRAINER excluded from CRUD) are covered separately
 * in {@code CourseCatalogSecurityTest} (MockMvc) since they're enforced at the SecurityConfig
 * matcher level, not in these services.
 */
@SpringBootTest
@Testcontainers
class CourseCatalogServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired DisciplineService disciplineService;
    @Autowired CourseService courseService;
    @Autowired PackageService packageService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired MemberService memberService;

    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired DisciplineRepository disciplineRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired UserRepository userRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        // Children first: subscriptions (member/type/package FKs) -> packages (join table) ->
        // courses (discipline/trainer FKs) -> disciplines -> trainers (app_users) -> members.
        subscriptionRepository.deleteAll();
        packageRepository.deleteAll();
        courseRepository.deleteAll();
        disciplineRepository.deleteAll();
        userRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Long userId, Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(userId, null, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Gym gym(String name) {
        return gymRepository.save(Gym.builder().name(name).build());
    }

    private AppUser trainer(Gym gym, String username) {
        return userRepository.save(AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("TRAINER")
                .gym(gym)
                .active(true)
                .mustChangePassword(true)
                .build());
    }

    private DisciplineResponseDTO discipline(String name) {
        DisciplineRequestDTO dto = new DisciplineRequestDTO();
        dto.setName(name);
        return disciplineService.create(dto);
    }

    private CourseRequestDTO courseRequest(Long disciplineId, Long trainerId, String name) {
        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setName(name);
        dto.setDisciplineId(disciplineId);
        dto.setTrainerId(trainerId);
        dto.setDaysOfWeek(List.of(1));
        dto.setStartTime(LocalTime.of(18, 0));
        dto.setDurationMin(60);
        return dto;
    }

    private PackageRequestDTO packageRequest(String name, BigDecimal price, int durationMonths, List<Long> courseIds) {
        PackageRequestDTO dto = new PackageRequestDTO();
        dto.setName(name);
        dto.setPrice(price);
        dto.setDurationMonths(durationMonths);
        dto.setCourseIds(courseIds);
        return dto;
    }

    private Long createMember(Gym gym, String email) {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Test");
        pi.setLastName("Member");
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        return memberService.create(req).getId();
    }

    // ── Discipline ───────────────────────────────────────────────────────

    @Test
    void discipline_sameNameInDifferentGyms_bothSucceed() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        discipline("Judo");

        actAs(2L, b.getId(), "GYM_MANAGER");
        DisciplineResponseDTO created = discipline("Judo");
        assertThat(created.getName()).isEqualTo("Judo");
    }

    @Test
    void discipline_duplicateNameInSameGym_returns409() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        discipline("Yoga");

        assertThatThrownBy(() -> discipline("yoga"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void discipline_crossGymRead_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Lotta").getId();

        actAs(2L, b.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> disciplineService.findById(disciplineId))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── Course ───────────────────────────────────────────────────────────

    @Test
    void course_withValidTrainerSameGym_succeeds() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("MMA").getId();
        AppUser t = trainer(a, "trainer.a@test.com");

        CourseResponseDTO created = courseService.create(courseRequest(disciplineId, t.getId(), "MMA Adulti"));
        assertThat(created.getTrainer()).isNotNull();
        assertThat(created.getTrainer().id()).isEqualTo(t.getId());
    }

    @Test
    void course_trainerWithWrongRole_returns422() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("MMA").getId();

        // A STAFF user is not a valid trainer, even in the same gym.
        AppUser staff = userRepository.save(AppUser.builder()
                .username("staff.a@test.com")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("STAFF")
                .gym(a)
                .active(true)
                .mustChangePassword(true)
                .build());

        assertThatThrownBy(() -> courseService.create(courseRequest(disciplineId, staff.getId(), "MMA Kids")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void course_canRunOnSeveralDays_storedSortedAndDeduped() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();

        CourseRequestDTO dto = courseRequest(disciplineId, null, "Judo Adulti");
        dto.setDaysOfWeek(List.of(5, 1, 3, 1)); // out of order, with a duplicate

        CourseResponseDTO created = courseService.create(dto);
        assertThat(created.getDaysOfWeek()).containsExactly(1, 3, 5);
        // Legacy mirror stays the first day, for clients not yet reading daysOfWeek.
        assertThat(created.getDayOfWeek()).isEqualTo(1);

        assertThat(courseService.findById(created.getId()).getDaysOfWeek()).containsExactly(1, 3, 5);
    }

    @Test
    void course_updateReplacesTheDays() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        Long courseId = courseService.create(courseRequest(disciplineId, null, "Judo")).getId();

        CourseRequestDTO update = courseRequest(disciplineId, null, "Judo");
        update.setDaysOfWeek(List.of(2, 4));

        assertThat(courseService.update(courseId, update).getDaysOfWeek()).containsExactly(2, 4);
        assertThat(courseService.findById(courseId).getDaysOfWeek()).containsExactly(2, 4);
    }

    @Test
    void course_legacySingleDayFieldStillAccepted() {
        // A client not yet updated sends only dayOfWeek — it must keep working during the
        // window where the backend is deployed ahead of the frontend.
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();

        CourseRequestDTO dto = courseRequest(disciplineId, null, "Judo Legacy");
        dto.setDaysOfWeek(null);
        dto.setDayOfWeek(4);

        assertThat(courseService.create(dto).getDaysOfWeek()).containsExactly(4);
    }

    @Test
    void course_withoutAnyDay_returns422() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();

        CourseRequestDTO dto = courseRequest(disciplineId, null, "Judo Senza Giorni");
        dto.setDaysOfWeek(List.of());
        dto.setDayOfWeek(null);

        assertThatThrownBy(() -> courseService.create(dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void findToday_matchesAnyOfTheCourseDays() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();

        int today = java.time.LocalDate.now().getDayOfWeek().getValue();
        int otherDay = today == 7 ? 1 : today + 1;

        CourseRequestDTO multi = courseRequest(disciplineId, null, "Corso di oggi");
        multi.setDaysOfWeek(List.of(otherDay, today)); // today is NOT the first day
        courseService.create(multi);

        CourseRequestDTO onlyOtherDay = courseRequest(disciplineId, null, "Corso di un altro giorno");
        onlyOtherDay.setDaysOfWeek(List.of(otherDay));
        courseService.create(onlyOtherDay);

        assertThat(courseService.findToday())
                .extracting(CourseResponseDTO::getName)
                .containsExactly("Corso di oggi");
    }

    @Test
    void course_gymManagerCanBeAssignedAsTeacher() {
        // In a small gym the manager teaches their own courses — resolveTrainer accepts
        // GYM_MANAGER alongside TRAINER (STAFF stays rejected, see the 422 test above).
        Gym a = gym("Gym A");
        AppUser manager = userRepository.save(AppUser.builder()
                .username("manager.a@test.com")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("GYM_MANAGER")
                .gym(a)
                .active(true)
                .mustChangePassword(true)
                .build());

        actAs(manager.getId(), a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();

        CourseResponseDTO created =
                courseService.create(courseRequest(disciplineId, manager.getId(), "Judo Adulti"));

        assertThat(created.getTrainer()).isNotNull();
        assertThat(created.getTrainer().id()).isEqualTo(manager.getId());
    }

    @Test
    void course_trainerOfAnotherGym_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");
        AppUser trainerB = trainer(b, "trainer.b@test.com");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("MMA").getId();

        assertThatThrownBy(() -> courseService.create(courseRequest(disciplineId, trainerB.getId(), "MMA Adulti")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void course_disciplineOfAnotherGym_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineIdA = discipline("Judo").getId();

        actAs(2L, b.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> courseService.create(courseRequest(disciplineIdA, null, "Judo Kids")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void course_crossGymRead_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        Long courseId = courseService.create(courseRequest(disciplineId, null, "Judo Kids")).getId();

        actAs(2L, b.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> courseService.findById(courseId))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── Package ──────────────────────────────────────────────────────────

    @Test
    void package_withCoursesFromSameGym_succeeds() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        Long course1 = courseService.create(courseRequest(disciplineId, null, "Judo Kids")).getId();
        Long course2 = courseService.create(courseRequest(disciplineId, null, "Judo Adulti")).getId();

        PackageResponseDTO pkg = packageService.create(
                packageRequest("Full Judo", new BigDecimal("59.90"), 3, List.of(course1, course2)));

        assertThat(pkg.getCourses()).hasSize(2);
        assertThat(pkg.getDurationMonths()).isEqualTo(3);
    }

    @Test
    void package_withCourseFromAnotherGym_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, b.getId(), "GYM_MANAGER");
        Long disciplineIdB = discipline("Yoga").getId();
        Long courseIdB = courseService.create(courseRequest(disciplineIdB, null, "Yoga Serale")).getId();

        actAs(2L, a.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> packageService.create(
                packageRequest("Cross-gym Package", new BigDecimal("30.00"), 1, List.of(courseIdB))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void package_crossGymRead_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        Long courseId = courseService.create(courseRequest(disciplineId, null, "Judo Kids")).getId();
        Long packageId = packageService.create(
                packageRequest("Judo Base", new BigDecimal("40.00"), 1, List.of(courseId))).getId();

        actAs(2L, b.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> packageService.findById(packageId))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── Subscription -> Package ─────────────────────────────────────────

    @Test
    void subscription_withPackageId_freezesPriceAndComputesEndDate() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        Long courseId = courseService.create(courseRequest(disciplineId, null, "Judo Kids")).getId();
        PackageResponseDTO pkg = packageService.create(
                packageRequest("Judo Base", new BigDecimal("50.00"), 2, List.of(courseId)));

        Long memberId = createMember(a, "pkg.member@test.com");

        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setPackageId(pkg.getId());
        dto.setStartDate(LocalDate.of(2026, 1, 1));

        SubscriptionResponseDTO sub = subscriptionService.create(memberId, dto);

        assertThat(sub.getType()).isNull();
        assertThat(sub.getTrainingPackage()).isNotNull();
        assertThat(sub.getTrainingPackage().getId()).isEqualTo(pkg.getId());
        assertThat(sub.getPurchasePrice()).isEqualByComparingTo("50.00");
        assertThat(sub.getPrice()).isEqualByComparingTo("50.00");
        assertThat(sub.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 1));

        // Listino price change afterwards must not rewrite the frozen snapshot.
        packageService.update(pkg.getId(), packageRequest("Judo Base", new BigDecimal("70.00"), 2, List.of(courseId)));
        List<SubscriptionResponseDTO> listed = subscriptionService.findByMember(memberId);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).getPurchasePrice()).isEqualByComparingTo("50.00");
        assertThat(listed.get(0).getType()).isNull(); // no NPE on toDTO for a package-based subscription
    }

    @Test
    void subscription_bothTypeAndPackage_returns422() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long memberId = createMember(a, "both.member@test.com");

        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setTypeId(1L);
        dto.setPackageId(1L);
        dto.setStartDate(LocalDate.now());
        dto.setPrice(new BigDecimal("10.00"));

        assertThatThrownBy(() -> subscriptionService.create(memberId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void subscription_neitherTypeNorPackage_returns422() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long memberId = createMember(a, "neither.member@test.com");

        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setStartDate(LocalDate.now());

        assertThatThrownBy(() -> subscriptionService.create(memberId, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void subscription_packageOfAnotherGym_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, b.getId(), "GYM_MANAGER");
        Long disciplineIdB = discipline("Yoga").getId();
        Long courseIdB = courseService.create(courseRequest(disciplineIdB, null, "Yoga Serale")).getId();
        Long packageIdB = packageService.create(
                packageRequest("Yoga Pack", new BigDecimal("20.00"), 1, List.of(courseIdB))).getId();

        actAs(2L, a.getId(), "GYM_MANAGER");
        Long memberIdA = createMember(a, "cross.member@test.com");

        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setPackageId(packageIdB);
        dto.setStartDate(LocalDate.now());

        assertThatThrownBy(() -> subscriptionService.create(memberIdA, dto))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void subscription_withTypeId_stillWorksAsToday() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long memberId = createMember(a, "type.member@test.com");

        // Subscription types are per-gym: a fixture scoped to gym A itself, not the globally
        // seeded MONTHLY row (which belongs to a different gym and would trip the anti-IDOR
        // scope check in SubscriptionService.create).
        Long typeId = subscriptionTypeId(a, "MONTHLY");

        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setTypeId(typeId);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setPrice(new BigDecimal("35.00"));

        SubscriptionResponseDTO sub = subscriptionService.create(memberId, dto);

        assertThat(sub.getType()).isNotNull();
        assertThat(sub.getTrainingPackage()).isNull();
        assertThat(sub.getPurchasePrice()).isNull();
        assertThat(sub.getPrice()).isEqualByComparingTo("35.00");
        assertThat(sub.getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Autowired
    com.gymmanager.repository.SubscriptionTypeRepository subscriptionTypeRepository;

    /** Subscription types are per-gym: reuse the gym's own type of that code if it already has
     *  one (e.g. seeded defaults), otherwise create a fixture scoped to it. */
    private Long subscriptionTypeId(Gym gym, String code) {
        return subscriptionTypeRepository.findByGymIdOrderByIdAsc(gym.getId()).stream()
                .filter(t -> code.equals(t.getCode()))
                .findFirst()
                .map(com.gymmanager.entity.SubscriptionType::getId)
                .orElseGet(() -> subscriptionTypeRepository.save(
                        com.gymmanager.entity.SubscriptionType.builder()
                                .code(code).label(code).months(1).gym(gym).build()
                ).getId());
    }

    // ── Trainer & member areas (/trainer/courses ownership, /me/courses catalog) ──

    @Test
    void trainerArea_findMyCourses_returnsOnlyOwnCourses() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Judo").getId();
        AppUser t1 = trainer(a, "trainer.one@test.com");
        AppUser t2 = trainer(a, "trainer.two@test.com");
        courseService.create(courseRequest(disciplineId, t1.getId(), "Judo Base"));
        courseService.create(courseRequest(disciplineId, t2.getId(), "Judo Avanzato"));

        actAs(t1.getId(), a.getId(), "TRAINER");
        List<CourseResponseDTO> mine = courseService.findMyCourses();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getName()).isEqualTo("Judo Base");
    }

    @Test
    void trainerArea_courseOwnedByAnotherTrainer_looksLikeNotFound() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Lotta").getId();
        AppUser owner = trainer(a, "owner@test.com");
        AppUser other = trainer(a, "other@test.com");
        Long courseId = courseService.create(courseRequest(disciplineId, owner.getId(), "Lotta Base")).getId();

        actAs(other.getId(), a.getId(), "TRAINER");
        assertThatThrownBy(() -> courseService.getCourseOwnedByCurrentTrainer(courseId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void trainerArea_ownCourse_isResolved() {
        Gym a = gym("Gym A");
        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineId = discipline("Karate").getId();
        AppUser owner = trainer(a, "karate.owner@test.com");
        Long courseId = courseService.create(courseRequest(disciplineId, owner.getId(), "Karate Kids")).getId();

        actAs(owner.getId(), a.getId(), "TRAINER");
        assertThat(courseService.getCourseOwnedByCurrentTrainer(courseId).getId()).isEqualTo(courseId);
    }

    @Test
    void meArea_findActiveForGym_returnsOnlyThatGymsActiveCourses() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(1L, a.getId(), "GYM_MANAGER");
        Long disciplineA = discipline("Yoga").getId();
        CourseRequestDTO active = courseRequest(disciplineA, null, "Yoga Mattina");
        courseService.create(active);
        CourseRequestDTO inactive = courseRequest(disciplineA, null, "Yoga Sera");
        inactive.setActive(false);
        courseService.create(inactive);

        actAs(2L, b.getId(), "GYM_MANAGER");
        Long disciplineB = discipline("Pilates").getId();
        courseService.create(courseRequest(disciplineB, null, "Pilates Base"));

        List<CourseResponseDTO> catalog = courseService.findActiveForGym(a.getId());
        assertThat(catalog).extracting(CourseResponseDTO::getName).containsExactly("Yoga Mattina");
    }
}
