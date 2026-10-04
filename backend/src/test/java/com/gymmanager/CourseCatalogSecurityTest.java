package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.GymRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level gate for the Corsi & Pacchetti feature (roadmap §4 §C): without explicit
 * matchers in SecurityConfig, /courses|/packages|/disciplines would fall through to
 * anyRequest().authenticated() and be reachable by ANY authenticated user. These tests
 * pin down that ADMIN, MEMBER and TRAINER are excluded, and STAFF/GYM_MANAGER are allowed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CourseCatalogSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GymRepository gymRepository;

    private Long testGymId;

    @BeforeEach
    void setUp() {
        testGymId = gymRepository.save(Gym.builder().name("Security Test Gym").build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static final String[] PATHS = {"/api/v1/disciplines", "/api/v1/courses", "/api/v1/packages"};

    /** Authenticated user with role + gym context (needed for the STAFF/GYM_MANAGER "allowed" cases,
     *  since the service layer requires a resolvable gymId — a role-only @WithMockUser has none). */
    private RequestPostProcessor as(String role, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        return authentication(auth);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_isExcludedFromCatalogRoutes() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void member_isExcludedFromCatalogRoutes() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void trainer_isExcludedFromCatalogManagementRoutes() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void staff_canReadCatalogRoutes() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path).with(as("STAFF", testGymId))).andExpect(status().isOk());
        }
    }

    @Test
    void gymManager_canReadCatalogRoutes() throws Exception {
        for (String path : PATHS) {
            mockMvc.perform(get(path).with(as("GYM_MANAGER", testGymId))).andExpect(status().isOk());
        }
    }

    @Test
    void trainerArea_isTrainerOnly() throws Exception {
        for (String role : new String[]{"ADMIN", "STAFF", "GYM_MANAGER", "MEMBER"}) {
            mockMvc.perform(get("/api/v1/trainer/courses").with(as(role, testGymId)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/trainer/courses").with(as("TRAINER", testGymId)))
                .andExpect(status().isOk());
    }

    @Test
    void meCourses_isMemberOnly() throws Exception {
        for (String role : new String[]{"ADMIN", "STAFF", "GYM_MANAGER", "TRAINER"}) {
            mockMvc.perform(get("/api/v1/me/courses").with(as(role, testGymId)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/me/courses").with(as("MEMBER", testGymId)))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_isRejectedFromCatalogRoutes() throws Exception {
        // No explicit AuthenticationEntryPoint is configured (no httpBasic/formLogin), so
        // Spring Security falls back to Http403ForbiddenEntryPoint for unauthenticated requests.
        for (String path : PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
