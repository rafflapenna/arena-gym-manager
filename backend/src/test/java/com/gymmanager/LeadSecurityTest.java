package com.gymmanager;

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
 * Route-level gate for the Prove/Lead feature (roadmap #8): without an explicit matcher in
 * SecurityConfig, /leads/** would fall through to anyRequest().authenticated() and be
 * reachable by ANY authenticated user. Clone of CourseCatalogSecurityTest — "the test that
 * would have caught the anyRequest bug".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LeadSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired GymRepository gymRepository;

    private Long testGymId;
    private static final String BASE_PATH = "/api/v1/leads";

    @BeforeEach
    void setUp() {
        testGymId = gymRepository.save(Gym.builder().name("Lead Security Test Gym").build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Authenticated user with role + gym context (the service layer requires a resolvable
     *  gymId — a role-only @WithMockUser has none). */
    private RequestPostProcessor as(String role, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        return authentication(auth);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_isExcludedFromLeadRoutes() throws Exception {
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void member_isExcludedFromLeadRoutes() throws Exception {
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void trainer_isExcludedFromLeadRoutes() throws Exception {
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isForbidden());
    }

    @Test
    void staff_canReadLeadRoutes() throws Exception {
        mockMvc.perform(get(BASE_PATH).with(as("STAFF", testGymId))).andExpect(status().isOk());
    }

    @Test
    void gymManager_canReadLeadRoutes() throws Exception {
        mockMvc.perform(get(BASE_PATH).with(as("GYM_MANAGER", testGymId))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void member_isExcludedFromLeadConfirmRoute() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRejectedFromLeadRoutes() throws Exception {
        // No explicit AuthenticationEntryPoint is configured (no httpBasic/formLogin), so
        // Spring Security falls back to Http403ForbiddenEntryPoint for unauthenticated requests.
        mockMvc.perform(get(BASE_PATH)).andExpect(status().isForbidden());
    }
}
