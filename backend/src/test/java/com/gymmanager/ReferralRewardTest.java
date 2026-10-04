package com.gymmanager;

import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.MemberResponseDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.dto.ReferralRewardResponseDTO;
import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.ReferralRewardRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.ReferralRewardService;
import com.gymmanager.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Porta un amico": a member who brings someone in earns a percentage off one subscription.
 *
 * These tests pin the rules that cost real money if they drift — when the reward is earned,
 * that it is earned only once per person brought in, and that it can only be spent once.
 */
@SpringBootTest
@Testcontainers
class ReferralRewardTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MemberService memberService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired ReferralRewardService referralRewardService;

    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ReferralRewardRepository rewardRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired SubscriptionStatusRepository subscriptionStatusRepository;

    private Gym gymA;
    private Long typeId;

    @BeforeEach
    void setUp() {
        // Dependency order: rewards point at subscriptions AND at members, and members point
        // at each other through referredBy — so detach that before deleting the rows.
        rewardRepository.deleteAll();
        subscriptionRepository.deleteAll();
        memberRepository.saveAll(memberRepository.findAll().stream()
                .peek(m -> m.setReferredBy(null))
                .toList());
        memberRepository.deleteAll();

        gymA = gymRepository.save(Gym.builder().name("Gym A " + System.nanoTime()).build());
        actAs(gymA.getId());

        subscriptionStatusRepository.findByCode("ACTIVE").orElseGet(() ->
                subscriptionStatusRepository.save(
                        SubscriptionStatus.builder().code("ACTIVE").label("Attivo").build()));

        typeId = subscriptionTypeRepository.save(SubscriptionType.builder()
                .code("MONTHLY_" + System.nanoTime())
                .label("Mensile")
                .months(1)
                .gym(gymA)
                .build()).getId();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_GYM_MANAGER")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "GYM_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MemberResponseDTO member(String email, Long referredByMemberId) {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Test");
        pi.setLastName(email.split("@")[0]);
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));

        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        req.setReferredByMemberId(referredByMemberId);
        return memberService.create(req);
    }

    private Long subscribe(Long memberId, BigDecimal price, Long rewardId) {
        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.setTypeId(typeId);
        dto.setStartDate(LocalDate.now());
        dto.setPrice(price);
        dto.setRewardId(rewardId);
        return subscriptionService.create(memberId, dto).getId();
    }

    // ── Earning ──────────────────────────────────────────────────────────

    @Test
    void reward_isEarnedOnlyWhenTheReferredMemberSubscribes() {
        Long referrer = member("referrer@test.com", null).getId();
        Long brought = member("brought@test.com", referrer).getId();

        // Registered but not yet enrolled: nothing owed. This is the whole point of hanging
        // the reward off the subscription instead of off member creation.
        assertThat(referralRewardService.findByMember(referrer, null)).isEmpty();

        subscribe(brought, new BigDecimal("50.00"), null);

        List<ReferralRewardResponseDTO> rewards = referralRewardService.findByMember(referrer, null);
        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(0).getPercent()).isEqualByComparingTo("20");
        assertThat(rewards.get(0).getStatus()).isEqualTo("EARNED");
        assertThat(rewards.get(0).getReferredMember().id()).isEqualTo(brought);
    }

    @Test
    void reward_isEarnedOncePerPersonBroughtIn_notOnEveryRenewal() {
        Long referrer = member("referrer@test.com", null).getId();
        Long brought = member("brought@test.com", referrer).getId();

        subscribe(brought, new BigDecimal("50.00"), null);
        subscribe(brought, new BigDecimal("50.00"), null); // rinnovo
        subscribe(brought, new BigDecimal("50.00"), null); // altro rinnovo

        assertThat(referralRewardService.findByMember(referrer, null)).hasSize(1);
    }

    @Test
    void reward_oneForEachDifferentPersonBroughtIn() {
        Long referrer = member("referrer@test.com", null).getId();
        Long first = member("first@test.com", referrer).getId();
        Long second = member("second@test.com", referrer).getId();

        subscribe(first, new BigDecimal("50.00"), null);
        subscribe(second, new BigDecimal("50.00"), null);

        assertThat(referralRewardService.findByMember(referrer, "EARNED")).hasSize(2);
    }

    @Test
    void reward_notEarnedWhenNobodyBroughtThemIn() {
        Long walkIn = member("walkin@test.com", null).getId();
        subscribe(walkIn, new BigDecimal("50.00"), null);

        assertThat(rewardRepository.count()).isZero();
    }

    // ── Spending ─────────────────────────────────────────────────────────

    @Test
    void reward_appliesThePercentageAndIsBurned() {
        Long referrer = member("referrer@test.com", null).getId();
        Long brought = member("brought@test.com", referrer).getId();
        subscribe(brought, new BigDecimal("50.00"), null);

        Long rewardId = referralRewardService.findByMember(referrer, "EARNED").get(0).getId();
        Long subscriptionId = subscribe(referrer, new BigDecimal("60.00"), rewardId);

        assertThat(subscriptionRepository.findById(subscriptionId).orElseThrow().getPrice())
                .isEqualByComparingTo("48.00"); // 60 - 20%

        ReferralRewardResponseDTO spent = referralRewardService.findByMember(referrer, null).get(0);
        assertThat(spent.getStatus()).isEqualTo("USED");
        assertThat(spent.getUsedOnSubscriptionId()).isEqualTo(subscriptionId);
    }

    @Test
    void reward_cannotBeSpentTwice() {
        Long referrer = member("referrer@test.com", null).getId();
        Long brought = member("brought@test.com", referrer).getId();
        subscribe(brought, new BigDecimal("50.00"), null);

        Long rewardId = referralRewardService.findByMember(referrer, "EARNED").get(0).getId();
        subscribe(referrer, new BigDecimal("60.00"), rewardId);

        assertThatThrownBy(() -> subscribe(referrer, new BigDecimal("60.00"), rewardId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void reward_ofAnotherMemberLooksLikeNotFound() {
        Long referrer = member("referrer@test.com", null).getId();
        Long brought = member("brought@test.com", referrer).getId();
        Long stranger = member("stranger@test.com", null).getId();
        subscribe(brought, new BigDecimal("50.00"), null);

        Long rewardId = referralRewardService.findByMember(referrer, "EARNED").get(0).getId();

        assertThatThrownBy(() -> subscribe(stranger, new BigDecimal("60.00"), rewardId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void subscriptionWithoutReward_keepsTheFullPrice() {
        Long walkIn = member("walkin@test.com", null).getId();
        Long subscriptionId = subscribe(walkIn, new BigDecimal("60.00"), null);

        assertThat(subscriptionRepository.findById(subscriptionId).orElseThrow().getPrice())
                .isEqualByComparingTo("60.00");
    }

    // ── Referrer validation ──────────────────────────────────────────────

    @Test
    void referrer_fromAnotherGymLooksLikeNotFound() {
        Long referrer = member("referrer@test.com", null).getId();

        Gym gymB = gymRepository.save(Gym.builder().name("Gym B " + System.nanoTime()).build());
        actAs(gymB.getId());

        assertThatThrownBy(() -> member("other.gym@test.com", referrer))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }
}
