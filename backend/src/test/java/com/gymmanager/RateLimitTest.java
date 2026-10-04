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
 * Forgot-password stays on the flat per-IP {@code RateLimitInterceptor} window (unchanged).
 * Login's own progressive, failure-only throttle is {@code LoginAttemptService}: the unit
 * math lives in {@code LoginAttemptServiceTest}, this class only proves the end-to-end HTTP
 * contract (429 + Retry-After + retryAfterSeconds) and anti-enumeration over real MockMvc
 * requests through {@code AuthController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void login_fifthRequestAfterFourConsecutiveFailures_returns429WithRetryAfterContract() throws Exception {
        // Unique IP + nonexistent username: the account need not exist for the throttle
        // to kick in (anti-enumeration — see the test below for the explicit comparison).
        // The 4th failure itself is still a normal 401 (accurate feedback on *that*
        // request) — it is what ARMS the block for the next (5th) attempt.
        String ip = "203.0.113.80";
        String body = objectMapper.writeValueAsString(new LoginBody("nobody-429@example.com", "wrong"));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30))
                .andExpect(jsonPath("$.detail").value("Troppi tentativi. Riprova tra 30 secondi."));
    }

    @Test
    void login_nonExistentUsername_isThrottledIdenticallyRegardlessOfWhichGhostAccount() throws Exception {
        // Two different never-registered addresses on two different IPs: both must follow
        // byte-for-byte the same 429 schedule — the throttle has no notion of "this account
        // doesn't exist, skip it", which is exactly what prevents user enumeration.
        String firstIp = "203.0.113.81";
        String secondIp = "203.0.113.82";
        String firstBody = objectMapper.writeValueAsString(new LoginBody("ghost-one@example.com", "whatever"));
        String secondBody = objectMapper.writeValueAsString(new LoginBody("ghost-two@example.com", "whatever"));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", firstIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstBody))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", secondIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(secondBody))
                    .andExpect(status().isUnauthorized());
        }

        // 5th request on each (after 4 consecutive failures): identical 429 + identical Retry-After.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", firstIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", secondIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    private record LoginBody(String username, String password) {}

    @Test
    void forgotPassword_isRateLimited_afterMaxRequestsPerWindow() throws Exception {
        // Unique test IP via X-Forwarded-For -> isolated rate-limit bucket,
        // independent from any other test hitting the same endpoint.
        String ip = "203.0.113.7";
        String body = "{\"email\":\"nobody@example.com\"}";

        // First 5 allowed: unknown email is silently skipped (204, no email sent).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        // 6th within the window is rejected with 429 + Retry-After.
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
