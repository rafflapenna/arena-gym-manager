package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.SubscriptionTypeRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.LookupService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level slice for {@code POST}/{@code PUT /api/v1/subscription-types/{id}} (roadmap:
 * wizard "Iscrizione & Incasso" §3-§4, later scoped per-gym by the listino migration).
 * Subscription types are per-gym data managed by the gym's own backoffice (STAFF/GYM_MANAGER)
 * only: ADMIN has no gym of its own to manage a price list for, so it's forbidden the same as
 * MEMBER/TRAINER/anonymous. These tests pin that down and cover the 404/422 mapping through the
 * real HTTP stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LookupControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LookupService lookupService;
    @Autowired GymRepository gymRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionStatusRepository subscriptionStatusRepository;
    @Autowired MemberRepository memberRepository;

    private Long gymId;

    @BeforeEach
    void setUp() {
        gymId = gymRepository.save(Gym.builder().name("Lookup Route Test Gym " + UUID.randomUUID()).build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createType(String code) {
        SubscriptionTypeRequestDTO dto = new SubscriptionTypeRequestDTO();
        dto.setCode(code);
        dto.setLabel("Route Test Type");
        dto.setMonths(1);
        return lookupService.createSubscriptionType(dto, gymId).getId();
    }

    private String body(String code, String label, int months, BigDecimal defaultPrice) throws Exception {
        SubscriptionTypeRequestDTO dto = new SubscriptionTypeRequestDTO();
        dto.setCode(code);
        dto.setLabel(label);
        dto.setMonths(months);
        dto.setDefaultPrice(defaultPrice);
        return objectMapper.writeValueAsString(dto);
    }

    /** STAFF/GYM_MANAGER principal carrying the test gym's id (same pattern as
     *  MultiTenantScopingTest), since the service now needs a real gymId to authorize the call. */
    private RequestPostProcessor asRole(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_isForbiddenFromUpdatingSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-ADMIN");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Updated Label", 6, new BigDecimal("99.90"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_updateNonExistentId_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/subscription-types/999999999")
                        .with(asRole("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("GHOST"), "Ghost", 1, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void staff_updateWithInvalidPrice_returns422() throws Exception {
        String code = uniqueCode("ROUTE-INVALID");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .with(asRole("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Bad Price", 1, new BigDecimal("0.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.defaultPrice").exists());
    }

    @Test
    void staff_canUpdateSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-STAFF");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .with(asRole("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Updated By Staff", 2, new BigDecimal("15.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Updated By Staff"))
                .andExpect(jsonPath("$.defaultPrice").value(15.00));
    }

    @Test
    void gymManager_canUpdateSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-GM");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .with(asRole("GYM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Updated By Gym Manager", 3, new BigDecimal("20.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Updated By Gym Manager"))
                .andExpect(jsonPath("$.defaultPrice").value(20.00));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void member_isForbiddenFromUpdatingSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-MEMBER");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Should Not Apply", 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void trainer_isForbiddenFromUpdatingSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-TRAINER");
        Long id = createType(code);

        mockMvc.perform(put("/api/v1/subscription-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "Should Not Apply", 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRejectedFromUpdatingSubscriptionType() throws Exception {
        mockMvc.perform(put("/api/v1/subscription-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ANY", "Any", 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_isForbiddenFromCreatingSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-CREATE");
        mockMvc.perform(post("/api/v1/subscription-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "New Type", 1, new BigDecimal("25.00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_canCreateSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-CREATE-STAFF");
        mockMvc.perform(post("/api/v1/subscription-types")
                        .with(asRole("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "New Type By Staff", 1, new BigDecimal("30.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultPrice").value(30.00));
    }

    @Test
    void gymManager_canCreateSubscriptionType() throws Exception {
        String code = uniqueCode("ROUTE-CREATE-GM");
        mockMvc.perform(post("/api/v1/subscription-types")
                        .with(asRole("GYM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "New Type By Gym Manager", 1, new BigDecimal("35.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultPrice").value(35.00));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void member_isForbiddenFromCreatingSubscriptionType() throws Exception {
        mockMvc.perform(post("/api/v1/subscription-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("ROUTE-CREATE-MEMBER"), "Should Not Apply", 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void trainer_isForbiddenFromCreatingSubscriptionType() throws Exception {
        mockMvc.perform(post("/api/v1/subscription-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("ROUTE-CREATE-TRAINER"), "Should Not Apply", 1, null)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/v1/subscription-types/{id} ─────────────────────────────

    @Test
    void staff_isForbiddenFromDeletingSubscriptionType() throws Exception {
        Long id = createType(uniqueCode("ROUTE-DEL-FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/subscription-types/" + id).with(asRole("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDeleteUnusedSubscriptionType() throws Exception {
        Long id = createType(uniqueCode("ROUTE-DEL-UNUSED"));

        mockMvc.perform(delete("/api/v1/subscription-types/" + id))
                .andExpect(status().isNoContent());

        assertThat(subscriptionTypeRepository.findById(id)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_deletingReferencedSubscriptionType_returns409() throws Exception {
        Long typeId = createType(uniqueCode("ROUTE-DEL-USED"));
        SubscriptionType type = subscriptionTypeRepository.findById(typeId).orElseThrow();

        PersonalInfo pi = new PersonalInfo();
        pi.setFirstName("Del");
        pi.setLastName("Test");
        pi.setEmail("del-test-" + UUID.randomUUID() + "@test.com");
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        Member member = memberRepository.save(Member.builder()
                .personalInfo(pi)
                .gym(gymRepository.findById(gymId).orElseThrow())
                .registrationDate(LocalDate.now())
                .build());

        SubscriptionStatus active = subscriptionStatusRepository.findByCode("ACTIVE").orElseThrow();
        subscriptionRepository.save(Subscription.builder()
                .member(member)
                .type(type)
                .status(active)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .price(new BigDecimal("30.00"))
                .build());

        mockMvc.perform(delete("/api/v1/subscription-types/" + typeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 abbonamenti")));

        assertThat(subscriptionTypeRepository.findById(typeId)).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_deletingLegacyGlobalSubscriptionType_returns409() throws Exception {
        SubscriptionType legacy = subscriptionTypeRepository.save(SubscriptionType.builder()
                .code(uniqueCode("LEGACY"))
                .label("Legacy Global")
                .months(1)
                .build());

        mockMvc.perform(delete("/api/v1/subscription-types/" + legacy.getId()))
                .andExpect(status().isConflict());

        assertThat(subscriptionTypeRepository.findById(legacy.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_deletingNonExistentSubscriptionType_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/subscription-types/999999999"))
                .andExpect(status().isNotFound());
    }
}
