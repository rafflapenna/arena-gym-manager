package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired GymRepository gymRepository;

    private Long testGymId;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        Gym gym = gymRepository.save(Gym.builder().name("Test Gym").build());
        testGymId = gym.getId();
        actAsStaff(testGymId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        memberRepository.deleteAll();
        gymRepository.deleteById(testGymId);
    }

    private void actAsStaff(Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "STAFF"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MemberRequestDTO buildRequest(String firstName, String lastName, String email) {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName(firstName);
        pi.setLastName(lastName);
        pi.setEmail(email);
        pi.setPhone("3331234567");
        pi.setBirthDate(LocalDate.of(1990, 5, 15));

        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        return req;
    }

    @Test
    void fullCrudCycle() throws Exception {
        MemberRequestDTO request = buildRequest("Mario", "Rossi", "mario.rossi@test.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.personalInfo.email").value("mario.rossi@test.com"))
                .andExpect(jsonPath("$.personalInfo.firstName").value("Mario"))
                .andExpect(jsonPath("$.personalInfo.lastName").value("Rossi"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // --- READ BY ID ---
        mockMvc.perform(get("/api/v1/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalInfo.lastName").value("Rossi"));

        // --- LIST ---
        mockMvc.perform(get("/api/v1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        // --- SEARCH ---
        mockMvc.perform(get("/api/v1/members").param("q", "mario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].personalInfo.firstName").value("Mario"));

        // --- UPDATE ---
        request.getPersonalInfo().setPhone("3339876543");
        mockMvc.perform(put("/api/v1/members/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalInfo.phone").value("3339876543"));

        // --- DEACTIVATE ---
        mockMvc.perform(patch("/api/v1/members/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // --- ACTIVATE ---
        mockMvc.perform(patch("/api/v1/members/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // --- DELETE ---
        mockMvc.perform(delete("/api/v1/members/{id}", id))
                .andExpect(status().isNoContent());

        // --- NOT FOUND ---
        mockMvc.perform(get("/api/v1/members/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        MemberRequestDTO request = buildRequest("Anna", "Verdi", "anna.verdi@test.com");

        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidEmailReturns422() throws Exception {
        MemberRequestDTO request = buildRequest("Test", "User", "not-valid");

        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors['personalInfo.email']").exists());
    }
}
