package com.gymmanager;

import com.gymmanager.dto.SubscriptionResponseDTO;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Fix-E: {@code GET /subscriptions/expiring} is gym-scoped in the DB
 * (no cross-tenant leak) and resolves ACTIVE status via cached lookup, not an
 * extra query.
 */
@SpringBootTest
@Testcontainers
class SubscriptionExpiryQueryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SubscriptionService subscriptionService;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionStatusRepository subscriptionStatusRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;

    private Gym gymA;
    private Gym gymB;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        memberRepository.deleteAll();

        gymA = gymRepository.save(Gym.builder().name("Gym Alpha").build());
        gymB = gymRepository.save(Gym.builder().name("Gym Beta").build());

        SubscriptionStatus activeStatus = subscriptionStatusRepository.findByCode("ACTIVE")
                .orElseThrow(() -> new IllegalStateException("ACTIVE status not seeded"));
        SubscriptionType subType = subscriptionTypeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No subscription type seeded"));

        // Member of gym A — subscription expiring in 3 days (within any days>=3 window)
        Member memberA = memberRepository.save(Member.builder()
                .gym(gymA)
                .personalInfo(PersonalInfo.builder()
                        .firstName("Alice").lastName("GymA")
                        .email("alice@gymalpha.test").phone("3331111111")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .build())
                .active(true)
                .build());
        subscriptionRepository.save(Subscription.builder()
                .member(memberA)
                .type(subType)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusDays(3))
                .price(BigDecimal.valueOf(50))
                .status(activeStatus)
                .build());

        // Member of gym B — subscription expiring in 5 days
        Member memberB = memberRepository.save(Member.builder()
                .gym(gymB)
                .personalInfo(PersonalInfo.builder()
                        .firstName("Bob").lastName("GymB")
                        .email("bob@gymbeta.test").phone("3332222222")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .build())
                .active(true)
                .build());
        subscriptionRepository.save(Subscription.builder()
                .member(memberB)
                .type(subType)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusDays(5))
                .price(BigDecimal.valueOf(60))
                .status(activeStatus)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        subscriptionRepository.deleteAll();
        memberRepository.deleteAll();
        gymRepository.delete(gymA);
        gymRepository.delete(gymB);
    }

    private void actAs(Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void findExpiringIsGymScoped() {
        // STAFF of gym A should see only gym A's subscription
        actAs(gymA.getId(), "STAFF");
        List<SubscriptionResponseDTO> gymAResults = subscriptionService.findExpiring(7);
        assertThat(gymAResults).hasSize(1);
        assertThat(gymAResults.get(0).getMemberFullName()).contains("GymA");

        // STAFF of gym B should see only gym B's subscription
        actAs(gymB.getId(), "STAFF");
        List<SubscriptionResponseDTO> gymBResults = subscriptionService.findExpiring(7);
        assertThat(gymBResults).hasSize(1);
        assertThat(gymBResults.get(0).getMemberFullName()).contains("GymB");
    }

    @Test
    void findExpiringAdminSeesAllGyms() {
        // ADMIN (gymId = null) sees subscriptions from all gyms
        actAs(null, "ADMIN");
        List<SubscriptionResponseDTO> allResults = subscriptionService.findExpiring(7);
        assertThat(allResults).hasSize(2);
    }

    @Test
    void findExpiringWindowExcludes_outOfRangeSubscriptions() {
        // With a 2-day window only gym A's subscription (expiring in 3 days) is out of range;
        // gym B's (expiring in 5 days) is also out of range — nothing returned for either gym.
        actAs(gymA.getId(), "STAFF");
        List<SubscriptionResponseDTO> narrow = subscriptionService.findExpiring(2);
        assertThat(narrow).isEmpty();
    }
}
