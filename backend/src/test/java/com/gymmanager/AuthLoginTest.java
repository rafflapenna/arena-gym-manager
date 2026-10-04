package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression test for the userDetails-cache/credential-erasure bug: after a successful
 * login, {@code DaoAuthenticationProvider} used to erase the password on the cached
 * {@code UserDetails} instance ({@code UserDetailsServiceImpl#loadUserByUsername} is
 * {@code @Cacheable}, 60s TTL, and returns that same shared instance to every caller),
 * so every login for that user within the TTL after the first compared bcrypt against a
 * {@code null} password and got a spurious 401. Fixed by disabling erasure on the
 * explicit {@code ProviderManager} in {@code SecurityConfig}.
 */
// app.seed.demo-users defaults to false in prod (round 2 hardening); this test logs in as
// the seeded demo accounts ("staff"/"admin"), so turn seeding back on just for this class.
@SpringBootTest(properties = "app.seed.demo-users=true")
@AutoConfigureMockMvc
@Testcontainers
class AuthLoginTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Unique per-test IP so this test's requests never share the login rate-limit
    // bucket (10 req / 5 min per IP) with any other test.
    private static final String IP = "203.0.113.55";

    @Test
    void consecutiveLoginsWithinTheCacheTtl_bothSucceed() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody("staff", "staff123"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("staff"));

        // Second login for the SAME user, immediately after (well within the 60s
        // userDetails cache TTL): must still succeed, not 401.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("staff"));
    }

    @Test
    void usernameIsCaseInsensitiveAndTrimmed() throws Exception {
        // iOS capitalises the first letter of the username field ("Staff"); copy-paste can
        // add spaces. Both must log into the stored lowercase account.
        String body = objectMapper.writeValueAsString(new LoginBody("  Staff ", "staff123"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", IP + "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("staff"));
    }

    @Test
    void wrongPassword_stillReturns401AfterAPriorSuccessfulLogin() throws Exception {
        String goodBody = objectMapper.writeValueAsString(new LoginBody("admin", "admin"));
        String badBody = objectMapper.writeValueAsString(new LoginBody("admin", "wrong-password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", IP + "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodBody))
                .andExpect(status().isOk());

        // Not eraseCredentials-immune to real wrong passwords: an actually invalid
        // password must still be rejected after a preceding successful login.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", IP + "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isUnauthorized());
    }

    private record LoginBody(String username, String password) {}
}
