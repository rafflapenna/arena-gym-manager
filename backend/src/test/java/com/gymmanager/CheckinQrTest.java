package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.MemberService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QR check-in (roadmap #5): security/IDOR, idempotency and token lifecycle coverage.
 * The route-level "falls through to anyRequest().authenticated()" bug from #1 is guarded
 * against explicitly (see {@link #memberAndAdmin_areForbiddenOnQrCheckIn}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckinQrTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired MemberService memberService;

    private Long gymAId;
    private Long gymBId;

    @BeforeEach
    void setUp() {
        checkInRepository.deleteAll();
        memberRepository.deleteAll();
        gymAId = gymRepository.save(Gym.builder().name("Gym A").build()).getId();
        gymBId = gymRepository.save(Gym.builder().name("Gym B").build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        checkInRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private RequestPostProcessor as(String role, Long gymId) {
        return as(role, gymId, null);
    }

    private RequestPostProcessor as(String role, Long gymId, Long memberId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, memberId, gymId, role));
        return authentication(auth);
    }

    /** Creates a member directly via the service (bypassing HTTP) as the given gym's manager. */
    private Long createMember(Long gymId, String email) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_GYM_MANAGER")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "GYM_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
            pi.setFirstName("Test");
            pi.setLastName("Member");
            pi.setEmail(email);
            pi.setPhone("3330000000");
            pi.setBirthDate(LocalDate.of(1990, 1, 1));
            MemberRequestDTO req = new MemberRequestDTO();
            req.setPersonalInfo(pi);
            return memberService.create(req).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String fetchToken(Long memberId, Long gymId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/members/{id}/qr", memberId).with(as("STAFF", gymId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String qrBody(String token) throws Exception {
        return objectMapper.writeValueAsString(new TokenBody(token));
    }

    private record TokenBody(String token) {}

    @Test
    void memberAndAdmin_areForbiddenOnQrCheckIn() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("MEMBER", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody("whatever")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("ADMIN", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody("whatever")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownToken_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody("does-not-exist")))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankToken_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.token").exists());
    }

    @Test
    void crossGymToken_returns404() throws Exception {
        Long memberBId = createMember(gymBId, "crossgym@test.com");
        String tokenB = fetchToken(memberBId, gymBId);

        // An operator of gym A scanning gym B's member card must get an indistinguishable 404.
        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(tokenB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void backofficeQrLookup_isScopedToOwnGym() throws Exception {
        Long memberAId = createMember(gymAId, "scoped@test.com");

        mockMvc.perform(get("/api/v1/members/{id}/qr", memberAId).with(as("STAFF", gymBId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/members/{id}/qr", memberAId).with(as("STAFF", gymAId)))
                .andExpect(status().isOk());
    }

    @Test
    void qrCheckIn_persistsSourceQr() throws Exception {
        Long memberId = createMember(gymAId, "qr@test.com");
        String token = fetchToken(memberId, gymAId);

        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("QR"))
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false));

        assertThat(checkInRepository.count()).isEqualTo(1);
        assertThat(checkInRepository.findByMemberIdWithDetails(memberId).get(0).getSource().name())
                .isEqualTo("QR");
    }

    @Test
    void idempotentAcrossChannels_receptionThenQr() throws Exception {
        Long memberId = createMember(gymAId, "crosschannel@test.com");

        // First: reception check-in.
        mockMvc.perform(post("/api/v1/members/{id}/check-ins", memberId).with(as("STAFF", gymAId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("RECEPTION"));

        // Same day, via QR: must be recognized as already-checked-in, not a new row.
        String token = fetchToken(memberId, gymAId);
        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCheckedIn").value(true))
                .andExpect(jsonPath("$.source").value("RECEPTION"));

        assertThat(checkInRepository.count()).isEqualTo(1);
    }

    @Test
    void meQrToken_isStableAcrossCalls() throws Exception {
        Long memberId = createMember(gymAId, "meqr@test.com");

        MvcResult first = mockMvc.perform(get("/api/v1/me/qr").with(as("MEMBER", gymAId, memberId)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/me/qr").with(as("MEMBER", gymAId, memberId)))
                .andExpect(status().isOk())
                .andReturn();

        String tokenFirst = objectMapper.readTree(first.getResponse().getContentAsString()).get("token").asText();
        String tokenSecond = objectMapper.readTree(second.getResponse().getContentAsString()).get("token").asText();
        assertThat(tokenFirst).isNotBlank();
        assertThat(tokenFirst).isEqualTo(tokenSecond);
    }

    @Test
    void rotate_invalidatesThePreviousToken() throws Exception {
        Long memberId = createMember(gymAId, "rotate@test.com");
        String oldToken = fetchToken(memberId, gymAId);

        MvcResult rotateResult = mockMvc.perform(
                        post("/api/v1/members/{id}/qr/rotate", memberId).with(as("STAFF", gymAId)))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = objectMapper.readTree(rotateResult.getResponse().getContentAsString()).get("token").asText();
        assertThat(newToken).isNotEqualTo(oldToken);

        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(oldToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/check-ins/qr").with(as("STAFF", gymAId))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(newToken)))
                .andExpect(status().isCreated());
    }
}
