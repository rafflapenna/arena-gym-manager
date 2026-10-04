package com.gymmanager;

import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.EmailOutboxRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.service.SubscriptionRenewalReminderJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SubscriptionRenewalReminderJob}: queues a reminder at the single strictest configured
 * threshold (default 7, 1 days before {@code endDate}) — never more than one the same night —
 * is idempotent on re-run, and skips a member who has already renewed.
 */
@SpringBootTest
@Testcontainers
class SubscriptionRenewalReminderJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SubscriptionRenewalReminderJob job;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionStatusRepository subscriptionStatusRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired EmailOutboxRepository outboxRepository;

    private Gym gym;
    private SubscriptionStatus activeStatus;
    private SubscriptionType subType;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        subscriptionRepository.deleteAll();
        memberRepository.deleteAll();

        gym = gymRepository.save(Gym.builder().name("Dojo " + System.nanoTime()).contactEmail("info@dojo.test").build());
        activeStatus = subscriptionStatusRepository.findByCode("ACTIVE").orElseThrow();
        subType = subscriptionTypeRepository.findAll().stream().findFirst().orElseThrow();
    }

    private Member member(String email) {
        return memberRepository.save(Member.builder()
                .gym(gym)
                .personalInfo(PersonalInfo.builder()
                        .firstName("Test").lastName("Member")
                        .email(email).phone("3330000000")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .build())
                .active(true)
                .build());
    }

    private Subscription subscription(Member member, LocalDate start, LocalDate end) {
        return subscriptionRepository.save(Subscription.builder()
                .member(member)
                .type(subType)
                .startDate(start)
                .endDate(end)
                .price(BigDecimal.valueOf(50))
                .status(activeStatus)
                .build());
    }

    @Test
    void sevenDaysOut_queuesExactlyOneReminder_atTheSevenDayThreshold() {
        Member m = member("renew7@dojo.test");
        subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(7));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("subrenew:\\d+:7");
        assertThat(queued.get(0).getToAddress()).isEqualTo("renew7@dojo.test");
        assertThat(queued.get(0).getReplyTo()).isEqualTo("info@dojo.test");
    }

    @Test
    void oneDayOut_queuesExactlyOneReminder_atTheOneDayThreshold() {
        Member m = member("renew1@dojo.test");
        subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(1));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("subrenew:\\d+:1");
    }

    @Test
    void threeDaysOut_queuesOnlyTheSevenDayThreshold_notBoth() {
        // Falls within both the 7- and 1-day windows, but only the strictest (smallest)
        // applicable threshold — "7" — must fire; never two emails the same night.
        Member m = member("renew3@dojo.test");
        subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(3));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("subrenew:\\d+:7");
    }

    @Test
    void rerunOnTheSameDayQueuesNothingNew() {
        Member m = member("rerun@dojo.test");
        subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(3));

        job.sendReminders();
        long firstRun = outboxRepository.count();
        job.sendReminders();
        long secondRun = outboxRepository.count();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void memberWhoAlreadyRenewedIsNotReminded() {
        Member m = member("already-renewed@dojo.test");
        Subscription expiring = subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(1));
        // Already bought the next one.
        subscription(m, expiring.getEndDate().plusDays(1), expiring.getEndDate().plusMonths(1));

        job.sendReminders();

        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void inactiveMemberIsNotReminded() {
        Member m = member("inactive@dojo.test");
        m.setActive(false);
        memberRepository.save(m);
        subscription(m, LocalDate.now().minusMonths(1), LocalDate.now().plusDays(1));

        job.sendReminders();

        assertThat(outboxRepository.findAll()).isEmpty();
    }
}
