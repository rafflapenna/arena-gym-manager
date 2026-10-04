package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.LeadRequestDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Lead;
import com.gymmanager.entity.LeadOrigin;
import com.gymmanager.entity.LeadStatus;
import com.gymmanager.entity.Member;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.LeadRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level tests for the Prove/Lead feature (roadmap #8): full CRUD cycle, bean-validation
 * 422s (only reachable through the MVC dispatch, unlike the service-level tests in
 * LeadServiceTest/LeadScopingTest which call the service directly and bypass @Valid), and the
 * convert endpoint's error contract end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LeadControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LeadRepository leadRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired GymRepository gymRepository;
    @Autowired MemberService memberService;

    private Long testGymId;

    @BeforeEach
    void setUp() {
        leadRepository.deleteAll();
        memberRepository.deleteAll();
        Gym gym = gymRepository.save(Gym.builder().name("Lead Controller Test Gym").build());
        testGymId = gym.getId();
        actAsStaff(testGymId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        leadRepository.deleteAll();
        memberRepository.deleteAll();
        gymRepository.deleteById(testGymId);
    }

    private void actAsStaff(Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "STAFF"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private LeadRequestDTO buildRequest(String firstName, String lastName, String phone) {
        LeadRequestDTO dto = new LeadRequestDTO();
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setBirthDate(LocalDate.of(1995, 6, 15));
        dto.setPhone(phone);
        return dto;
    }

    @Test
    void fullCrudCycle() throws Exception {
        LeadRequestDTO request = buildRequest("Mario", "Rossi", "3331234567");

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // --- READ BY ID ---
        mockMvc.perform(get("/api/v1/leads/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Rossi"));

        // --- LIST ---
        mockMvc.perform(get("/api/v1/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // --- LIST filtered by status ---
        mockMvc.perform(get("/api/v1/leads").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/leads").param("status", "LOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // --- LIST with an invalid status value: must be a clean 400, never a 500 (status is
        // bound as a plain String on the controller precisely to avoid an unhandled
        // MethodArgumentTypeMismatchException here) ---
        mockMvc.perform(get("/api/v1/leads").param("status", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest());

        // --- SEARCH ---
        mockMvc.perform(get("/api/v1/leads").param("q", "mario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Mario"));

        // --- UPDATE ---
        request.setPhone("3339876543");
        mockMvc.perform(put("/api/v1/leads/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("3339876543"));

        // --- DELETE ---
        mockMvc.perform(delete("/api/v1/leads/{id}", id))
                .andExpect(status().isNoContent());

        // --- NOT FOUND ---
        mockMvc.perform(get("/api/v1/leads/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingBirthDate_returns422() throws Exception {
        LeadRequestDTO request = buildRequest("Mario", "Rossi", "3331234567");
        request.setBirthDate(null);

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.birthDate").exists());
    }

    @Test
    void missingPhone_returns422() throws Exception {
        LeadRequestDTO request = buildRequest("Mario", "Rossi", null);

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void futureBirthDate_returns422() throws Exception {
        LeadRequestDTO request = buildRequest("Mario", "Rossi", "3331234567");
        request.setBirthDate(LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.birthDate").exists());
    }

    @Test
    void convert_missingEmail_returns422() throws Exception {
        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void convert_malformedEmail_returns422() throws Exception {
        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void convert_success_createsMemberAndReturns200() throws Exception {
        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mario.rossi@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("CONVERTED"))
                .andExpect(jsonPath("$.lead.convertedMemberId").isNumber())
                .andExpect(jsonPath("$.member.personalInfo.email").value("mario.rossi@test.com"))
                .andExpect(jsonPath("$.member.personalInfo.phone").value("3331234567"));
    }

    @Test
    void convert_emailAlreadyUsedByAnotherMember_returns409() throws Exception {
        // Seed an existing member with the email the lead will try to convert with.
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personalInfo":{"firstName":"Existing","lastName":"Member",
                                "email":"taken@test.com","phone":"3330000000","birthDate":"1990-01-01"}}
                                """))
                .andExpect(status().isCreated());

        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taken@test.com\"}"))
                .andExpect(status().isConflict());
    }

    // ── REQUESTED / origin / confirm / decline ("prove inserite dai soci") ──

    @Test
    void createWithRequestedStatus_returns422() throws Exception {
        LeadRequestDTO request = buildRequest("Mario", "Rossi", "3331234567");
        request.setStatus(LeadStatus.REQUESTED);

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void updateToRequestedStatus_onNonRequestedLead_returns422() throws Exception {
        Long id = createLead("Mario", "Rossi"); // starts NEW, never REQUESTED
        LeadRequestDTO request = buildRequest("Mario", "Rossi", "3331234567");
        request.setStatus(LeadStatus.REQUESTED);

        mockMvc.perform(put("/api/v1/leads/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void confirm_movesRequestedLeadToTrialScheduled() throws Exception {
        Long id = memberReportedLeadId();
        String trialDate = LocalDate.now().plusDays(2).toString();

        mockMvc.perform(post("/api/v1/leads/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trialDate\":\"" + trialDate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL_SCHEDULED"))
                .andExpect(jsonPath("$.trialDate").value(trialDate));
    }

    @Test
    void confirm_onNonRequestedLead_returns409WithCode() throws Exception {
        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trialDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_REQUESTED"));
    }

    @Test
    void decline_movesRequestedLeadToLost() throws Exception {
        Long id = memberReportedLeadId();

        mockMvc.perform(post("/api/v1/leads/{id}/decline", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));
    }

    @Test
    void decline_onNonRequestedLead_returns409WithCode() throws Exception {
        Long id = createLead("Mario", "Rossi");

        mockMvc.perform(post("/api/v1/leads/{id}/decline", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_REQUESTED"));
    }

    @Test
    void convert_onRequestedLead_returns409WithCode() throws Exception {
        Long id = memberReportedLeadId();

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@test.com\",\"birthDate\":\"1995-05-05\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_CONFIRMED"));
    }

    @Test
    void convert_memberReportedLead_withoutBirthDate_returns422() throws Exception {
        Long id = memberReportedLeadId();
        confirmLead(id);

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@test.com\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.birthDate").exists());
    }

    @Test
    void convert_memberReportedLead_withBirthDateInBody_savesItOnLeadAndMember() throws Exception {
        Long id = memberReportedLeadId();
        confirmLead(id);

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend@test.com\",\"birthDate\":\"1995-05-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("CONVERTED"))
                .andExpect(jsonPath("$.lead.birthDate").value("1995-05-05"))
                .andExpect(jsonPath("$.member.personalInfo.birthDate").value("1995-05-05"));

        assertThat(leadRepository.findById(id).orElseThrow().getBirthDate())
                .isEqualTo(LocalDate.of(1995, 5, 5));
    }

    @Test
    void convert_replayOfConvertedLead_returns200() throws Exception {
        Long id = createLead("Mario", "Rossi");
        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mario.rossi@test.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/leads/{id}/convert", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone.else@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("CONVERTED"))
                .andExpect(jsonPath("$.member.personalInfo.email").value("mario.rossi@test.com"));
    }

    @Test
    void putOnMemberOriginLead_withNullReferredByMemberId_keepsReferrer() throws Exception {
        Member friend = createMember("referrer@test.com");
        Long id = memberReportedLeadId(friend);

        LeadRequestDTO update = buildRequest("Mario", "Updated", "3339999999");
        update.setBirthDate(LocalDate.of(1990, 1, 1));
        update.setReferredByMemberId(null);

        MvcResult result = mockMvc.perform(put("/api/v1/leads/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andReturn();

        Long referredById = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("referredBy").get("id").asLong();
        assertThat(referredById).isEqualTo(friend.getId());
    }

    @Test
    void findAll_filterByOrigin() throws Exception {
        createLead("Mario", "Rossi"); // origin STAFF
        memberReportedLeadId(); // origin MEMBER

        mockMvc.perform(get("/api/v1/leads").param("origin", "MEMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("MEMBER"));

        mockMvc.perform(get("/api/v1/leads").param("origin", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("STAFF"));
    }

    @Test
    void findAll_invalidOrigin_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/leads").param("origin", "XYZ"))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers for "prove inserite dai soci" scenarios ─────────────────────

    private Member createMember(String email) {
        // Re-assert the ambient auth right before this direct service-bean call: a prior
        // mockMvc round-trip through the real security filter chain can clear the
        // SecurityContextHolder that @BeforeEach populated, which a plain mockMvc call never
        // notices (it goes through the filter chain itself) but a direct service call does.
        actAsStaff(testGymId);
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Friend");
        pi.setLastName("Referrer");
        pi.setEmail(email);
        pi.setPhone("3550000000");
        pi.setBirthDate(LocalDate.of(1988, 3, 3));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        Long memberId = memberService.create(req).getId();
        return memberRepository.findById(memberId).orElseThrow();
    }

    /**
     * Directly persists a MEMBER-origin REQUESTED lead, bypassing {@code POST /me/leads}
     * (covered end-to-end by {@code MeLeadControllerTest}) — here we only need staff-side
     * reactions (confirm/decline/convert/origin filter) to a lead that already is one.
     */
    private Long memberReportedLeadId() {
        return memberReportedLeadId(createMember("friendref" + System.nanoTime() + "@test.com"));
    }

    private Long memberReportedLeadId(Member referrer) {
        Gym gym = gymRepository.findById(testGymId).orElseThrow();
        Lead lead = Lead.builder()
                .gym(gym)
                .firstName("Amico")
                .lastName("Segnalato")
                .phone("3661234567")
                .origin(LeadOrigin.MEMBER)
                .status(LeadStatus.REQUESTED)
                .referredBy(referrer)
                .build();
        return leadRepository.save(lead).getId();
    }

    private void confirmLead(Long id) throws Exception {
        mockMvc.perform(post("/api/v1/leads/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trialDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isOk());
    }

    private Long createLead(String firstName, String lastName) throws Exception {
        LeadRequestDTO request = buildRequest(firstName, lastName, "3331234567");
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
