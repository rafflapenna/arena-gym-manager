package com.gymmanager;

import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.MemberFee;
import com.gymmanager.entity.MemberFeeType;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.EmailOutboxRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberFeeRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.service.FeeReminderJob;
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
 * {@link FeeReminderJob}: queues a reminder at the single strictest configured threshold
 * (default 30, 7 days before {@code validUntil}) — never more than one the same night —
 * is idempotent on re-run, and skips a member who has already renewed.
 */
@SpringBootTest
@Testcontainers
class FeeReminderJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired FeeReminderJob job;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberFeeRepository memberFeeRepository;
    @Autowired EmailOutboxRepository outboxRepository;

    private Gym gym;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        memberFeeRepository.deleteAll();
        memberRepository.deleteAll();

        gym = gymRepository.save(Gym.builder().name("Dojo " + System.nanoTime()).build());
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

    private MemberFee fee(Member member, LocalDate validUntil) {
        return fee(member, MemberFeeType.ASSOCIATION, validUntil);
    }

    private MemberFee fee(Member member, MemberFeeType type, LocalDate validUntil) {
        return memberFeeRepository.save(MemberFee.builder()
                .member(member)
                .type(type)
                .amount(BigDecimal.valueOf(20))
                .paidOn(validUntil.minusYears(1))
                .validUntil(validUntil)
                .build());
    }

    @Test
    void thirtyDaysOut_queuesExactlyOneReminder_atTheThirtyDayThreshold() {
        Member m = member("fee30@dojo.test");
        fee(m, LocalDate.now().plusDays(30));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("fee:\\d+:30");
        assertThat(queued.get(0).getToAddress()).isEqualTo("fee30@dojo.test");
    }

    @Test
    void sevenDaysOut_queuesExactlyOneReminder_atTheSevenDayThreshold() {
        Member m = member("fee7@dojo.test");
        fee(m, LocalDate.now().plusDays(7));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("fee:\\d+:7");
    }

    @Test
    void twentyDaysOut_queuesOnlyTheThirtyDayThreshold_notBoth() {
        // Falls within both the 30- and 7-day windows, but only the strictest (smallest)
        // applicable threshold — "30" — must fire; never two emails the same night.
        Member m = member("fee20@dojo.test");
        fee(m, LocalDate.now().plusDays(20));

        job.sendReminders();

        List<EmailOutbox> queued = outboxRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getDedupKey()).matches("fee:\\d+:30");
    }

    @Test
    void rerunOnTheSameDayQueuesNothingNew() {
        Member m = member("rerun-fee@dojo.test");
        fee(m, LocalDate.now().plusDays(20));

        job.sendReminders();
        long firstRun = outboxRepository.count();
        job.sendReminders();
        long secondRun = outboxRepository.count();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void inactiveMemberIsNotReminded() {
        Member m = member("inactive-fee@dojo.test");
        m.setActive(false);
        memberRepository.save(m);
        fee(m, LocalDate.now().plusDays(7));

        job.sendReminders();

        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void memberWhoAlreadyRenewedTheAssociationFeeIsNotReminded() {
        Member m = member("already-renewed-fee@dojo.test");
        fee(m, LocalDate.now().plusDays(7));
        // Already paid next year's quota — a later ASSOCIATION fee already covers the period
        // after the expiring one.
        fee(m, LocalDate.now().plusDays(7).plusYears(1));

        job.sendReminders();

        assertThat(outboxRepository.findAll()).isEmpty();
    }
}
