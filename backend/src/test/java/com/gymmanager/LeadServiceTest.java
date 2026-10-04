package com.gymmanager;

import com.gymmanager.dto.LeadConversionResponseDTO;
import com.gymmanager.dto.LeadConvertRequestDTO;
import com.gymmanager.dto.LeadRequestDTO;
import com.gymmanager.dto.LeadResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.LeadStatus;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.LeadRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.LeadService;
import com.gymmanager.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for the Prove/Lead feature (roadmap #8): the default-status derivation
 * rule and the convert-to-member flow (idempotency, field mapping, duplicate-email 409).
 * Route-level 403 checks live in LeadSecurityTest; cross-gym scoping in LeadScopingTest;
 * bean-validation 422s (only reachable through the MVC dispatch) in LeadControllerTest.
 */
@SpringBootTest
@Testcontainers
class LeadServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LeadService leadService;
    @Autowired MemberService memberService;

    @Autowired GymRepository gymRepository;
    @Autowired LeadRepository leadRepository;
    @Autowired MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        leadRepository.deleteAll();
        memberRepository.deleteAll();
        Gym gym = gymRepository.save(Gym.builder().name("Lead Service Test Gym").build());
        actAs(gym.getId(), "STAFF");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private LeadRequestDTO leadRequest(LocalDate trialDate) {
        LeadRequestDTO dto = new LeadRequestDTO();
        dto.setFirstName("Mario");
        dto.setLastName("Rossi");
        dto.setBirthDate(LocalDate.of(1995, 6, 15));
        dto.setPhone("3331234567");
        dto.setTrialDate(trialDate);
        return dto;
    }

    private LeadConvertRequestDTO convertRequest(String email) {
        LeadConvertRequestDTO dto = new LeadConvertRequestDTO();
        dto.setEmail(email);
        return dto;
    }

    // ── Default status (DESIGN-prove-lead.it.md §3) ─────────────────────────

    @Test
    void noTrialDate_defaultsToNew() {
        LeadResponseDTO lead = leadService.create(leadRequest(null));
        assertThat(lead.getStatus()).isEqualTo("NEW");
    }

    @Test
    void futureTrialDate_defaultsToTrialScheduled() {
        LeadResponseDTO lead = leadService.create(leadRequest(LocalDate.now().plusDays(3)));
        assertThat(lead.getStatus()).isEqualTo("TRIAL_SCHEDULED");
    }

    @Test
    void todayOrPastTrialDate_defaultsToTried() {
        LeadResponseDTO today = leadService.create(leadRequest(LocalDate.now()));
        assertThat(today.getStatus()).isEqualTo("TRIED");

        LeadResponseDTO past = leadService.create(leadRequest(LocalDate.now().minusDays(1)));
        assertThat(past.getStatus()).isEqualTo("TRIED");
    }

    @Test
    void explicitStatus_overridesTheDefault() {
        LeadRequestDTO dto = leadRequest(null);
        dto.setStatus(LeadStatus.LOST);
        assertThat(leadService.create(dto).getStatus()).isEqualTo("LOST");
    }

    /** An invalid ?status= must raise a handled 400, never let LeadStatus.valueOf's
     *  IllegalArgumentException escape and fall into the generic 500 handler. */
    @Test
    void findAll_invalidStatus_returns400() {
        assertThatThrownBy(() -> leadService.findAll("NOT_A_REAL_STATUS", null, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> assertThat(((org.springframework.web.server.ResponseStatusException) ex)
                        .getStatusCode().value()).isEqualTo(400));
    }

    // ── Convert ──────────────────────────────────────────────────────────

    @Test
    void convert_createsMemberWithLeadsBirthDateAndPhone() {
        Long leadId = leadService.create(leadRequest(LocalDate.now())).getId();

        LeadConversionResponseDTO result = leadService.convert(leadId, convertRequest("mario.rossi@test.com"));

        assertThat(result.getLead().getStatus()).isEqualTo("CONVERTED");
        assertThat(result.getLead().getConvertedMemberId()).isEqualTo(result.getMember().getId());
        assertThat(result.getMember().getPersonalInfo().getBirthDate()).isEqualTo(LocalDate.of(1995, 6, 15));
        assertThat(result.getMember().getPersonalInfo().getPhone()).isEqualTo("3331234567");
        assertThat(result.getMember().getPersonalInfo().getEmail()).isEqualTo("mario.rossi@test.com");
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    void convert_isIdempotent_secondCallCreatesNoSecondMember() {
        Long leadId = leadService.create(leadRequest(LocalDate.now())).getId();

        LeadConversionResponseDTO first = leadService.convert(leadId, convertRequest("mario.rossi@test.com"));
        LeadConversionResponseDTO second = leadService.convert(leadId, convertRequest("someone.else@test.com"));

        assertThat(second.getLead().getStatus()).isEqualTo("CONVERTED");
        assertThat(second.getMember().getId()).isEqualTo(first.getMember().getId());
        // The replay's email is ignored — the member keeps the email from the first conversion.
        assertThat(second.getMember().getPersonalInfo().getEmail()).isEqualTo("mario.rossi@test.com");
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    void convert_emailAlreadyUsedByAnotherMember_returns409() {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Existing");
        pi.setLastName("Member");
        pi.setEmail("taken@test.com");
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO existing = new MemberRequestDTO();
        existing.setPersonalInfo(pi);
        memberService.create(existing);

        Long leadId = leadService.create(leadRequest(LocalDate.now())).getId();

        assertThatThrownBy(() -> leadService.convert(leadId, convertRequest("taken@test.com")))
                .isInstanceOf(ConflictException.class);
    }
}
