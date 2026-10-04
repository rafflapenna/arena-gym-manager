package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.CreateInstructorRequestDTO;
import com.gymmanager.dto.GymRequestDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.GymService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GymControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired com.gymmanager.repository.FederationRepository federationRepository;
    @Autowired com.gymmanager.repository.FederationCardTypeRepository federationCardTypeRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GymRepository gymRepository;
    @Autowired UserRepository userRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired GymService gymService;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        // Order: members first (FK to gym), then users (FK to gym), then the per-gym
        // subscription types seeded by SubscriptionTypeSeeder on gym creation, then gyms
        memberRepository.deleteAll();
        userRepository.deleteAll();
        subscriptionTypeRepository.findAll().stream()
                .filter(t -> t.getGym() != null)
                .forEach(subscriptionTypeRepository::delete);
        federationCardTypeRepository.deleteAll();
        federationRepository.deleteAll();
        gymRepository.deleteAll();
    }

    // ── /api/v1/users/instructors — createInstructor ──────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInstructor_nonExistentGym_returns404() throws Exception {
        CreateInstructorRequestDTO dto = new CreateInstructorRequestDTO(
                "inst@test.com", "Foo", "Bar", 999_999L);

        mockMvc.perform(post("/api/v1/users/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInstructor_gymAlreadyHasInstructor_returns409() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Test Gym").build());

        // Seed an existing instructor for that gym
        userRepository.save(AppUser.builder()
                .username("existing.inst@test.com")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("GYM_MANAGER")
                .gym(gym)
                .mustChangePassword(true)
                .build());

        CreateInstructorRequestDTO dto = new CreateInstructorRequestDTO(
                "new.inst@test.com", "New", "Inst", gym.getId());

        mockMvc.perform(post("/api/v1/users/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInstructor_missingGymId_returns422() throws Exception {
        // gymId is null → @NotNull violation → 422 with errors map
        String payload = """
                {"email":"inst@test.com","firstName":"Foo","lastName":"Bar","gymId":null}
                """;

        mockMvc.perform(post("/api/v1/users/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.gymId").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInstructor_missingEmail_returns422() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Gym 422").build());
        // email is blank → @NotBlank violation → 422
        CreateInstructorRequestDTO dto = new CreateInstructorRequestDTO(
                "", "Foo", "Bar", gym.getId());

        mockMvc.perform(post("/api/v1/users/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ── GymService.delete ─────────────────────────────────────────────────

    @Test
    void deleteGym_withLinkedUser_returns409() {
        Gym gym = gymRepository.save(Gym.builder().name("Linked Gym").build());
        userRepository.save(AppUser.builder()
                .username("linked.user@test.com")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("GYM_MANAGER")
                .gym(gym)
                .mustChangePassword(true)
                .build());

        assertThatThrownBy(() -> gymService.delete(gym.getId()))
                .isInstanceOf(com.gymmanager.exception.ConflictException.class)
                .hasMessageContaining("Cannot delete gym");
    }

    @Test
    void deleteGym_withLinkedMember_returns409() {
        Gym gym = gymRepository.save(Gym.builder().name("Member Gym").build());

        PersonalInfo pi = new PersonalInfo();
        pi.setFirstName("Test");
        pi.setLastName("Member");
        pi.setEmail("test.member@gym.com");
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));

        Member member = Member.builder()
                .personalInfo(pi)
                .gym(gym)
                .registrationDate(LocalDate.now())
                .build();
        memberRepository.save(member);

        assertThatThrownBy(() -> gymService.delete(gym.getId()))
                .isInstanceOf(com.gymmanager.exception.ConflictException.class)
                .hasMessageContaining("Cannot delete gym");
    }

    @Test
    void deleteGym_empty_succeeds() {
        Gym gym = gymRepository.save(Gym.builder().name("Empty Gym").build());
        gymService.delete(gym.getId());
        assertThat(gymRepository.findById(gym.getId())).isEmpty();
    }

    @Test
    void deleteGym_createdThroughTheService_succeeds() {
        // Il test sopra salva la palestra dal repository, quindi senza listino ne' federazioni:
        // passava mentre in produzione la cancellazione falliva con un 409 di chiave esterna.
        // Una palestra creata davvero nasce con le righe seminate dai due seeder, ed e' quella
        // configurazione che va rimossa insieme a lei.
        Long gymId = gymService.create(new GymRequestDTO(
                "Seeded Gym", null, null, null, null, null, null, null, null, null)).id();

        assertThat(subscriptionTypeRepository.findByGymIdOrderByIdAsc(gymId)).isNotEmpty();
        assertThat(federationRepository.findByGymIdOrderByNameAsc(gymId)).isNotEmpty();

        gymService.delete(gymId);

        assertThat(gymRepository.findById(gymId)).isEmpty();
        assertThat(subscriptionTypeRepository.findByGymIdOrderByIdAsc(gymId)).isEmpty();
        assertThat(federationRepository.findByGymIdOrderByNameAsc(gymId)).isEmpty();
    }

    // ── GET /api/v1/gyms — non-ADMIN authenticated user ──────────────────

    @Test
    @WithMockUser(roles = "GYM_MANAGER")
    void listGyms_asInstructor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/gyms"))
                .andExpect(status().isForbidden());
    }

    // ── /api/v1/gyms/me — instructor self-manages own gym ────────────────

    private RequestPostProcessor as(String role, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "u", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        return authentication(auth);
    }

    @Test
    void instructor_canUpdateOwnGym() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("My Gym").build());
        GymRequestDTO dto = new GymRequestDTO(
                "My Gym Renamed", "Via Roma 1", "Milano", "20100", "MI", "Lombardia", "021234567", null, null, null);

        mockMvc.perform(put("/api/v1/gyms/me")
                        .with(as("GYM_MANAGER", gym.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Gym Renamed"))
                .andExpect(jsonPath("$.city").value("Milano"));
    }

    @Test
    @WithMockUser(roles = "GYM_MANAGER")
    void comuni_endpoint_returnsSeededList() throws Exception {
        mockMvc.perform(get("/api/v1/comuni"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").exists())
                .andExpect(jsonPath("$[0].sigla").exists())
                .andExpect(jsonPath("$[0].regione").exists());
    }

    @Test
    void staff_cannotUpdateGym() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Staff Gym").build());
        GymRequestDTO dto = new GymRequestDTO("Hacked", null, null, null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/v1/gyms/me")
                        .with(as("STAFF", gym.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }
}
