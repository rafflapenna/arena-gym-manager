package com.gymmanager;

import com.gymmanager.dto.CourseRequestDTO;
import com.gymmanager.dto.DisciplineRequestDTO;
import com.gymmanager.dto.LeadConvertRequestDTO;
import com.gymmanager.dto.LeadRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.DisciplineRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.LeadRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.CourseService;
import com.gymmanager.service.DisciplineService;
import com.gymmanager.service.LeadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Multi-tenant scoping for the Prove/Lead feature (roadmap #8), clone of
 * MultiTenantScopingTest: a lead created in gym A must be invisible to gym B, and every
 * single-lead operation (get/update/delete/convert) on a foreign lead must look like
 * "not found" (404) rather than leak via a 403 or a different error shape (anti-IDOR).
 */
@SpringBootTest
@Testcontainers
class LeadScopingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LeadService leadService;
    @Autowired DisciplineService disciplineService;
    @Autowired CourseService courseService;

    @Autowired GymRepository gymRepository;
    @Autowired LeadRepository leadRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired DisciplineRepository disciplineRepository;
    @Autowired MemberRepository memberRepository;

    @BeforeEach
    void clean() {
        // Children first: leads/courses (discipline FK) -> disciplines -> members.
        leadRepository.deleteAll();
        courseRepository.deleteAll();
        disciplineRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Gym gym(String name) {
        return gymRepository.save(Gym.builder().name(name).build());
    }

    private LeadRequestDTO leadRequest(String first, String last, String phone) {
        LeadRequestDTO dto = new LeadRequestDTO();
        dto.setFirstName(first);
        dto.setLastName(last);
        dto.setBirthDate(LocalDate.of(1995, 6, 15));
        dto.setPhone(phone);
        return dto;
    }

    /** Creates a discipline + course scoped to the given gym; caller must already be actAs(gym). */
    private Long courseInGym(String name) {
        DisciplineRequestDTO disciplineDto = new DisciplineRequestDTO();
        disciplineDto.setName(name + " discipline");
        Long disciplineId = disciplineService.create(disciplineDto).getId();

        CourseRequestDTO courseDto = new CourseRequestDTO();
        courseDto.setName(name);
        courseDto.setDisciplineId(disciplineId);
        courseDto.setDaysOfWeek(java.util.List.of(1));
        courseDto.setStartTime(LocalTime.of(18, 0));
        courseDto.setDurationMin(60);
        return courseService.create(courseDto).getId();
    }

    private void assertStatus404(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void leadsAreIsolatedPerGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        leadService.create(leadRequest("Mario", "A", "3330000001"));
        assertThat(leadService.findAll(null, null, null)).hasSize(1);

        actAs(b.getId(), "STAFF");
        assertThat(leadService.findAll(null, null, null)).isEmpty();
        leadService.create(leadRequest("Luigi", "B", "3330000002"));
        assertThat(leadService.findAll(null, null, null)).hasSize(1);
    }

    @Test
    void cannotReadALeadOfAnotherGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        Long leadId = leadService.create(leadRequest("Mario", "A", "3330000001")).getId();

        actAs(b.getId(), "STAFF");
        assertStatus404(() -> leadService.findById(leadId));
    }

    @Test
    void cannotUpdateALeadOfAnotherGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        Long leadId = leadService.create(leadRequest("Mario", "A", "3330000001")).getId();

        actAs(b.getId(), "STAFF");
        assertStatus404(() -> leadService.update(leadId, leadRequest("Mario", "Updated", "3330000009")));
    }

    @Test
    void cannotDeleteALeadOfAnotherGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        Long leadId = leadService.create(leadRequest("Mario", "A", "3330000001")).getId();

        actAs(b.getId(), "STAFF");
        assertStatus404(() -> leadService.delete(leadId));
    }

    @Test
    void cannotConvertALeadOfAnotherGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        Long leadId = leadService.create(leadRequest("Mario", "A", "3330000001")).getId();

        actAs(b.getId(), "STAFF");
        LeadConvertRequestDTO convertDto = new LeadConvertRequestDTO();
        convertDto.setEmail("mario.a@test.com");
        assertStatus404(() -> leadService.convert(leadId, convertDto));
    }

    @Test
    void courseOfAnotherGym_onCreate_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(b.getId(), "STAFF");
        Long courseIdB = courseInGym("Yoga Serale");

        actAs(a.getId(), "STAFF");
        LeadRequestDTO dto = leadRequest("Mario", "A", "3330000001");
        dto.setCourseId(courseIdB);

        assertStatus404(() -> leadService.create(dto));
    }

    @Test
    void courseOfAnotherGym_onUpdate_returns404() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        actAs(a.getId(), "STAFF");
        Long leadId = leadService.create(leadRequest("Mario", "A", "3330000001")).getId();

        actAs(b.getId(), "STAFF");
        Long courseIdB = courseInGym("Yoga Serale");

        actAs(a.getId(), "STAFF");
        LeadRequestDTO dto = leadRequest("Mario", "A", "3330000001");
        dto.setCourseId(courseIdB);

        assertStatus404(() -> leadService.update(leadId, dto));
    }
}
