package com.gymmanager;

import com.gymmanager.entity.EmailCategory;
import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.EmailOutboxStatus;
import com.gymmanager.repository.EmailOutboxRepository;
import com.gymmanager.service.EmailOutboxDrainJob;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * {@link EmailOutboxDrainJob}: claim/send/apply-outcome, retry with backoff, giving up after
 * {@code max-attempts}, and the shared daily cap. {@code app.mail.outbox.drain-fixed-delay-ms}
 * is pushed out to an hour (see {@code application-test.yml}) so the real {@code @Scheduled}
 * trigger never fires mid-test — every call to {@code drain()} here is explicit.
 */
@SpringBootTest(properties = {
        "app.mail.outbox.daily-cap=2",
        "app.mail.outbox.max-attempts=3",
        "app.mail.outbox.send-delay-ms=0"
})
@Testcontainers
class EmailOutboxDrainJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired EmailOutboxDrainJob drainJob;
    @Autowired EmailOutboxRepository outboxRepository;

    @MockBean
    JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private EmailOutbox queue(String dedupKey) {
        return queue(dedupKey, "member@test.it");
    }

    private EmailOutbox queue(String dedupKey, String toAddress) {
        LocalDateTime now = LocalDateTime.now();
        EmailOutbox row = EmailOutbox.builder()
                .fromAddress("noreply@test.it")
                .fromDisplayName("Test Gym")
                .toAddress(toAddress)
                .subject("Subject")
                .body("Body")
                .category(EmailCategory.REMINDER)
                .status(EmailOutboxStatus.PENDING)
                .attempts(0)
                .scheduledAt(now)
                .nextAttemptAt(now)
                .dedupKey(dedupKey)
                .build();
        return outboxRepository.save(row);
    }

    /** Fast-forwards a still-PENDING row past its backoff, standing in for the real wait. */
    private void makeClaimableNow(Long id) {
        EmailOutbox row = outboxRepository.findById(id).orElseThrow();
        row.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        outboxRepository.save(row);
    }

    @Test
    void transientFailure_retriesWithBackoffThenEventuallySucceeds() {
        EmailOutbox row = queue("retry-1");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.getAndIncrement() < 2) {
                throw new org.springframework.mail.MailSendException("smtp down");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        drainJob.drain();
        EmailOutbox after1 = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(after1.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(after1.getAttempts()).isEqualTo(1);

        makeClaimableNow(row.getId());
        drainJob.drain();
        EmailOutbox after2 = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(after2.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(after2.getAttempts()).isEqualTo(2);

        makeClaimableNow(row.getId());
        drainJob.drain();
        EmailOutbox after3 = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(after3.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(after3.getSentAt()).isNotNull();
    }

    @Test
    void transientFailure_isMarkedFailedAfterMaxAttempts() {
        // max-attempts=3 for this test class.
        EmailOutbox row = queue("fail-1");
        doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(MimeMessage.class));

        for (int i = 0; i < 3; i++) {
            drainJob.drain();
            EmailOutbox current = outboxRepository.findById(row.getId()).orElseThrow();
            if (current.getStatus() == EmailOutboxStatus.PENDING) {
                makeClaimableNow(row.getId());
            }
        }

        EmailOutbox finalRow = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(finalRow.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(finalRow.getAttempts()).isEqualTo(3);
    }

    @Test
    void malformedAddress_isMarkedFailedImmediately_noRetry() {
        // Double "@" is invalid per RFC 822 and rejected by strict InternetAddress parsing
        // inside MimeMessageHelper.setTo — a real permanent failure, not a mocked one.
        EmailOutbox row = queue("bad-address", "invalid@@example.com");

        drainJob.drain();

        EmailOutbox after = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getLastError()).isNotBlank();
    }

    @Test
    void dailyCap_isRespected_acrossCategoriesAndRows() {
        // daily-cap=2 for this test class; one SENT "today" already consumed, so only 1 is
        // left in the budget — only one of the two new PENDING rows should go out.
        EmailOutbox already = queue("already-sent");
        already.setStatus(EmailOutboxStatus.SENT);
        already.setSentAt(LocalDateTime.now());
        outboxRepository.save(already);

        queue("cap-1");
        queue("cap-2");

        drainJob.drain();

        long sentCount = outboxRepository.findAll().stream()
                .filter(e -> e.getStatus() == EmailOutboxStatus.SENT).count();
        long pendingCount = outboxRepository.findAll().stream()
                .filter(e -> e.getStatus() == EmailOutboxStatus.PENDING).count();
        assertThat(sentCount).isEqualTo(2);
        assertThat(pendingCount).isEqualTo(1);
    }
}
