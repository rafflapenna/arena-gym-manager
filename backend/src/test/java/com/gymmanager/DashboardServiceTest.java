package com.gymmanager;

import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PaymentRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PaymentMethodRepository;
import com.gymmanager.repository.PaymentRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.DashboardService;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.PaymentService;
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
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DashboardServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired DashboardService dashboardService;
    @Autowired MemberService memberService;
    @Autowired SubscriptionService subscriptionService;
    @Autowired PaymentService paymentService;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired PaymentMethodRepository paymentMethodRepository;

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        memberRepository.deleteAll();
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

    private Long createMember(Long gymId, String email, LocalDate registrationDate) {
        actAs(gymId, "GYM_MANAGER");
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Test");
        pi.setLastName("Member");
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        req.setRegistrationDate(registrationDate);
        return memberService.create(req).getId();
    }

    /** Subscription types are per-gym: reuse the gym's own MONTHLY type if it already has one
     *  (e.g. seeded for the default gym), otherwise create a fixture scoped to it. Looking this
     *  up by code alone (globally) would resolve to an arbitrary gym's row and trip the
     *  anti-IDOR scope check in SubscriptionService.create for any other gym. */
    private Long ensureMonthlyType(Long gymId) {
        return subscriptionTypeRepository.findByGymIdOrderByIdAsc(gymId).stream()
                .filter(t -> "MONTHLY".equals(t.getCode()))
                .findFirst()
                .map(SubscriptionType::getId)
                .orElseGet(() -> subscriptionTypeRepository.save(
                        SubscriptionType.builder().code("MONTHLY").label("Monthly").months(1)
                                .gym(gymRepository.getReferenceById(gymId)).build()
                ).getId());
    }

    private void createPayment(Long gymId, Long memberId, BigDecimal amount, LocalDate paymentDate) {
        actAs(gymId, "GYM_MANAGER");
        SubscriptionRequestDTO subDto = new SubscriptionRequestDTO();
        subDto.setTypeId(ensureMonthlyType(gymId));
        subDto.setStartDate(paymentDate);
        subDto.setPrice(amount);
        Long subscriptionId = subscriptionService.create(memberId, subDto).getId();

        PaymentRequestDTO payDto = new PaymentRequestDTO();
        payDto.setAmount(amount);
        payDto.setPaymentDate(paymentDate);
        payDto.setPaymentMethodId(paymentMethodRepository.findByCode("CASH").orElseThrow().getId());
        paymentService.create(subscriptionId, payDto);
    }

    @Test
    void statsAreScopedToCurrentGymAndCurrentMonth() {
        Gym gymA = gymRepository.save(Gym.builder().name("Gym A").build());
        Gym gymB = gymRepository.save(Gym.builder().name("Gym B").build());

        LocalDate today = LocalDate.now();
        LocalDate firstOfThisMonth = YearMonth.from(today).atDay(1);
        LocalDate lastMonth = firstOfThisMonth.minusDays(1);

        // Gym A: one member registered this month, one registered last month.
        Long memberThisMonthA = createMember(gymA.getId(), "this-month-a@test.com", firstOfThisMonth);
        createMember(gymA.getId(), "last-month-a@test.com", lastMonth);

        // Gym B: one member registered this month (must not leak into Gym A's stats).
        Long memberThisMonthB = createMember(gymB.getId(), "this-month-b@test.com", firstOfThisMonth);

        // Payments: two this month for Gym A (30 + 20), one last month for Gym A (excluded),
        // one this month for Gym B (excluded from Gym A's revenue).
        createPayment(gymA.getId(), memberThisMonthA, new BigDecimal("30.00"), today);
        createPayment(gymA.getId(), memberThisMonthA, new BigDecimal("20.00"), firstOfThisMonth);
        createPayment(gymB.getId(), memberThisMonthB, new BigDecimal("99.00"), today);

        actAs(gymA.getId(), "GYM_MANAGER");
        var statsA = dashboardService.getStats();
        assertThat(statsA.getMonthlyRevenue()).isEqualByComparingTo("50.00");
        assertThat(statsA.getNewMembersThisMonth()).isEqualTo(1);

        actAs(gymB.getId(), "STAFF");
        var statsB = dashboardService.getStats();
        assertThat(statsB.getMonthlyRevenue()).isEqualByComparingTo("99.00");
        assertThat(statsB.getNewMembersThisMonth()).isEqualTo(1);
    }

    @Test
    void statsAreZeroWhenNoDataThisMonth() {
        Gym gym = gymRepository.save(Gym.builder().name("Empty Gym").build());
        actAs(gym.getId(), "STAFF");

        var stats = dashboardService.getStats();
        assertThat(stats.getMonthlyRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getNewMembersThisMonth()).isZero();
    }
}
