package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.CourseRequestDTO;
import com.gymmanager.dto.DisciplineRequestDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.CourseRepository;
import com.gymmanager.repository.DisciplineRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.LeadRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.ReferralRewardRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.CourseService;
import com.gymmanager.service.DisciplineService;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.SubscriptionService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.gymmanager.entity.Federation;
import com.gymmanager.entity.MemberFederationCard;
import com.gymmanager.repository.FederationRepository;
import com.gymmanager.repository.MemberFederationCardRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Prove inserite dai soci" (2026-10-01): a MEMBER reporting a friend's free trial from
 * {@code /me/leads}. Uses a real {@link AuthPrincipal} (not {@code @WithMockUser}) since the
 * service layer needs a resolvable memberId/gymId, same convention as
 * {@code MemberDocumentSecurityTest}. Staff-side reactions to a self-reported lead
 * (confirm/decline/convert/origin) are covered in {@code LeadControllerTest}; this class
 * covers the MEMBER-facing contract: anti-abuse checks, anti-enumeration, the narrower
 * response DTO, and {@code rewardEarned}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MeLeadControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired MemberService memberService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired DisciplineService disciplineService;
    @Autowired CourseService courseService;

    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired LeadRepository leadRepository;
    @Autowired ReferralRewardRepository rewardRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired SubscriptionStatusRepository subscriptionStatusRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired DisciplineRepository disciplineRepository;

    private Long gymId;
    private Long typeId;

    @Autowired MemberFederationCardRepository cardRepository;
    @Autowired FederationRepository federationRepository;

    @BeforeEach
    void setUp() {
        cardRepository.deleteAll();
        rewardRepository.deleteAll();
        subscriptionRepository.deleteAll();
        leadRepository.deleteAll();
        courseRepository.deleteAll();
        disciplineRepository.deleteAll();
        memberRepository.saveAll(memberRepository.findAll().stream()
                .peek(m -> m.setReferredBy(null))
                .toList());
        memberRepository.deleteAll();

        Gym gym = gymRepository.save(Gym.builder().name("MeLead Test Gym " + System.nanoTime()).build());
        gymId = gym.getId();

        subscriptionStatusRepository.findByCode("ACTIVE").orElseGet(() ->
                subscriptionStatusRepository.save(
                        SubscriptionStatus.builder().code("ACTIVE").label("Attivo").build()));

        typeId = subscriptionTypeRepository.save(SubscriptionType.builder()
                .code("MONTHLY_" + System.nanoTime())
                .label("Mensile")
                .months(1)
                .gym(gym)
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Auth helpers ─────────────────────────────────────────────────────

    private RequestPostProcessor asMember(Long memberId, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        auth.setDetails(new AuthPrincipal(1L, memberId, gymId, "MEMBER"));
        return authentication(auth);
    }

    private RequestPostProcessor asStaff(Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "STAFF"));
        return authentication(auth);
    }

    /** Sets the ambient security context (not request-scoped) for a direct service-bean call. */
    private void actAsStaffFor(Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_GYM_MANAGER")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "GYM_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── Fixture helpers ──────────────────────────────────────────────────

    private Long createMember(String email) {
        return createMember(email, "350" + (System.nanoTime() % 10_000_000L));
    }

    private Long createMember(String email, String phone) {
        actAsStaffFor(gymId);
        try {
            PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
            pi.setFirstName("Test");
            pi.setLastName("Member");
            pi.setEmail(email);
            pi.setPhone(phone);
            pi.setBirthDate(LocalDate.of(1990, 1, 1));
            MemberRequestDTO req = new MemberRequestDTO();
            req.setPersonalInfo(pi);
            return memberService.create(req).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long subscribe(Long memberId, BigDecimal price, Long rewardId) {
        actAsStaffFor(gymId);
        try {
            SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
            dto.setTypeId(typeId);
            dto.setStartDate(LocalDate.now());
            dto.setPrice(price);
            dto.setRewardId(rewardId);
            return subscriptionService.create(memberId, dto).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long courseInGym(Long forGymId, String name) {
        actAsStaffFor(forGymId);
        try {
            DisciplineRequestDTO disciplineDto = new DisciplineRequestDTO();
            disciplineDto.setName(name + " discipline " + System.nanoTime());
            Long disciplineId = disciplineService.create(disciplineDto).getId();

            CourseRequestDTO courseDto = new CourseRequestDTO();
            courseDto.setName(name);
            courseDto.setDisciplineId(disciplineId);
            courseDto.setDaysOfWeek(List.of(1));
            courseDto.setStartTime(LocalTime.of(18, 0));
            courseDto.setDurationMin(60);
            return courseService.create(courseDto).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String phoneBody(String phone) {
        return "{\"firstName\":\"Amico\",\"lastName\":\"Test\",\"phone\":\"" + phone + "\"}";
    }

    private Long createMeLead(Long memberId, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/me/leads").with(asMember(memberId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody(phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createStaffLead(String firstName, String lastName, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads").with(asStaff(gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","birthDate":"1990-01-01","phone":"%s"}
                                """.formatted(firstName, lastName, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── POST /me/leads — happy path ─────────────────────────────────────

    @Test
    void create_happyPath_forcesReferrerAndIgnoresBirthDateAndReferredByInBody() throws Exception {
        Long meId = createMember("me1@test.com");
        Long decoyId = createMember("decoy1@test.com");

        String body = """
                {"firstName":"Amico","lastName":"Mio","phone":"3661110000","referredByMemberId":%d}
                """.formatted(decoyId);

        MvcResult result = mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Amico"))
                .andExpect(jsonPath("$.lastName").value("Mio"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();

        Long leadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        var saved = leadRepository.findById(leadId).orElseThrow();
        assertThat(saved.getReferredBy().getId()).isEqualTo(meId);
        assertThat(saved.getOrigin()).isEqualTo(com.gymmanager.entity.LeadOrigin.MEMBER);
        assertThat(saved.getBirthDate()).isNull();
    }

    // ── Validation ───────────────────────────────────────────────────────

    @Test
    void create_missingRequiredFields_returns422() throws Exception {
        Long meId = createMember("me2@test.com");

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void create_trialDateInThePast_returns422() throws Exception {
        Long meId = createMember("me3@test.com");
        String body = """
                {"firstName":"A","lastName":"B","phone":"3661110001","trialDate":"%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.trialDate").exists());
    }

    @Test
    void create_courseOfAnotherGym_returns404() throws Exception {
        Long meId = createMember("me4@test.com");

        Gym otherGym = gymRepository.save(Gym.builder().name("Other Gym " + System.nanoTime()).build());
        Long foreignCourseId = courseInGym(otherGym.getId(), "Yoga");

        String body = """
                {"firstName":"A","lastName":"B","phone":"3661110002","courseId":%d}
                """.formatted(foreignCourseId);

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_ownPhone_returns422() throws Exception {
        Long meId = createMember("me5@test.com", "3225557777");

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3225557777")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    // ── Anti-abuse: pending limit ────────────────────────────────────────

    @Test
    void create_sixthPendingLead_returns409() throws Exception {
        Long meId = createMember("me6@test.com");
        for (int i = 0; i < 5; i++) {
            createMeLead(meId, "36600000" + i);
        }

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3660000099")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_PENDING_LIMIT"));
    }

    @Test
    void pendingLimit_isFreedOnceALeadIsDeclined() throws Exception {
        Long meId = createMember("me7@test.com");
        List<Long> leadIds = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leadIds.add(createMeLead(meId, "36610000" + i));
        }

        // Reception declines one -> LOST, outside the PENDING set -> frees a slot.
        mockMvc.perform(post("/api/v1/leads/{id}/decline", leadIds.get(0)).with(asStaff(gymId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3661000099")))
                .andExpect(status().isCreated());
    }

    // ── Anti-abuse: duplicate phone among OPEN leads ────────────────────

    @Test
    void create_duplicatePhoneDifferentFormatting_returns409() throws Exception {
        Long meId = createMember("me8@test.com");
        // Staff-created OPEN lead with the Italian-formatted version of the same number.
        createStaffLead("X", "Y", "+39 333 1234567");

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3331234567")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_DUPLICATE_PHONE"));
    }

    @Test
    void convertedLead_doesNotBlockDuplicatePhone() throws Exception {
        Long meId = createMember("me9@test.com");
        Long staffLeadId = createStaffLead("X", "Y", "3339998888");
        mockMvc.perform(post("/api/v1/leads/{id}/convert", staffLeadId).with(asStaff(gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"convertedguy9@test.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3339998888")))
                .andExpect(status().isCreated());
    }

    @Test
    void declinedLead_doesNotBlockDuplicatePhone() throws Exception {
        Long meId = createMember("me10@test.com");
        // decline only accepts a REQUESTED lead (LEAD_NOT_REQUESTED otherwise) — a
        // self-reported one, not a staff-created NEW one (that path is covered by the
        // CONVERTED case above).
        Long otherMemberId = createMember("other10decline@test.com");
        Long requestedLeadId = createMeLead(otherMemberId, "3339997777");
        mockMvc.perform(post("/api/v1/leads/{id}/decline", requestedLeadId).with(asStaff(gymId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3339997777")))
                .andExpect(status().isCreated());
    }

    // ── Anti user-enumeration: phone of an existing member ──────────────

    @Test
    void create_phoneOfAnotherMember_succeedsWithNoHint_butMatchedMemberIdIsSetForStaff() throws Exception {
        Long meId = createMember("me11@test.com");
        Long otherMemberId = createMember("other11@test.com", "3335554444");

        MvcResult result = mockMvc.perform(post("/api/v1/me/leads").with(asMember(meId, gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneBody("3335554444")))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("matchedMemberId").doesNotContain("\"phone\"");

        Long leadId = objectMapper.readTree(json).get("id").asLong();

        MvcResult staffResult = mockMvc.perform(get("/api/v1/leads/{id}", leadId).with(asStaff(gymId)))
                .andExpect(status().isOk())
                .andReturn();
        Long matchedId = objectMapper.readTree(staffResult.getResponse().getContentAsString())
                .get("matchedMemberId").asLong();
        assertThat(matchedId).isEqualTo(otherMemberId);
    }

    // ── GET /me/leads ────────────────────────────────────────────────────

    @Test
    void findMine_includesOwnAndStaffReportedLeads_excludesContactFields() throws Exception {
        Long meId = createMember("me12@test.com");
        Long strangerId = createMember("stranger12@test.com");

        createMeLead(meId, "3661230001");

        mockMvc.perform(post("/api/v1/leads").with(asStaff(gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Staff","lastName":"Reported","birthDate":"1990-01-01",
                                 "phone":"3661230002","referredByMemberId":%d}
                                """.formatted(meId)))
                .andExpect(status().isCreated());

        // A lead belonging to someone else's referral must not leak into my list.
        createMeLead(strangerId, "3661230003");

        MvcResult result = mockMvc.perform(get("/api/v1/me/leads").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("\"phone\"").doesNotContain("\"email\"").doesNotContain("\"notes\"");
    }

    // ── rewardEarned lifecycle ───────────────────────────────────────────

    @Test
    void rewardEarned_falseThenTrueAfterSubscription_staysTrueAfterUsed() throws Exception {
        Long meId = createMember("me13@test.com");

        Long leadId = createMeLead(meId, "3669991111");

        mockMvc.perform(post("/api/v1/leads/{id}/confirm", leadId).with(asStaff(gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trialDate\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk());

        MvcResult convertResult = mockMvc.perform(post("/api/v1/leads/{id}/convert", leadId).with(asStaff(gymId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"friend13@test.com\",\"birthDate\":\"1995-01-01\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long convertedMemberId = objectMapper.readTree(convertResult.getResponse().getContentAsString())
                .get("member").get("id").asLong();

        mockMvc.perform(get("/api/v1/me/leads").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rewardEarned").value(false));

        subscribe(convertedMemberId, new BigDecimal("50.00"), null);

        mockMvc.perform(get("/api/v1/me/leads").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rewardEarned").value(true));

        Long rewardId = rewardRepository.findByBeneficiaryId(meId).get(0).getId();
        subscribe(meId, new BigDecimal("60.00"), rewardId);

        mockMvc.perform(get("/api/v1/me/leads").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rewardEarned").value(true));
    }

    // ── Security ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "STAFF")
    void staff_isForbiddenFromMeLeadsRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/leads")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GYM_MANAGER")
    void gymManager_isForbiddenFromMeLeadsRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/leads")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_isForbiddenFromMeLeadsRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/leads")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void trainer_isForbiddenFromMeLeadsRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/leads")).andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRejectedFromMeLeadsRoutes() throws Exception {
        // Same fallback as every other route in this app (no custom AuthenticationEntryPoint
        // configured): Http403ForbiddenEntryPoint, so this is a 403 not a 401 — see
        // LeadSecurityTest.anonymous_isRejectedFromLeadRoutes for the identical reasoning.
        mockMvc.perform(get("/api/v1/me/leads")).andExpect(status().isForbidden());
    }

    // ── GET /me (own profile) ────────────────────────────────────────────

    @Test
    void me_returnsOnlyNameEmailAndBelt_neverTheSensitiveData() throws Exception {
        Long meId = createMember("profile@test.it", "3501234567");

        mockMvc.perform(get("/api/v1/me").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalInfo.firstName").value("Test"))
                .andExpect(jsonPath("$.personalInfo.email").value("profile@test.it"))
                .andExpect(jsonPath("$.personalInfo.phone").doesNotExist())
                .andExpect(jsonPath("$.personalInfo.fiscalCode").doesNotExist())
                .andExpect(jsonPath("$.personalInfo.birthDate").doesNotExist())
                .andExpect(jsonPath("$.personalInfo.address").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void me_federationCards_returnsOwnCardNumbers() throws Exception {
        Long meId = createMember("cards@test.it");
        Long otherId = createMember("other-cards@test.it");
        Gym gym = gymRepository.findById(gymId).orElseThrow();
        Federation fk = federationRepository.save(
                Federation.builder().gym(gym).name("FederKombat").seasonStartMonth(9).build());
        cardRepository.save(MemberFederationCard.builder()
                .member(memberRepository.findById(meId).orElseThrow()).federation(fk).cardNumber("483572").build());
        cardRepository.save(MemberFederationCard.builder()
                .member(memberRepository.findById(otherId).orElseThrow()).federation(fk).cardNumber("999999").build());

        mockMvc.perform(get("/api/v1/me/federation-cards").with(asMember(meId, gymId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].federation.name").value("FederKombat"))
                .andExpect(jsonPath("$[0].cardNumber").value("483572"));
    }
}
