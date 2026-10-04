package com.gymmanager;

import com.gymmanager.dto.MedicalCertExpiringDTO;
import com.gymmanager.dto.MemberDocumentResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.DocumentType;
import com.gymmanager.entity.EmailOutbox;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.EmailOutboxRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MedicalCertReminderLogRepository;
import com.gymmanager.repository.MemberDocumentBlobRepository;
import com.gymmanager.repository.MemberDocumentRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.MedicalCertReminderJob;
import com.gymmanager.service.MemberDocumentService;
import com.gymmanager.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for the Member Documents feature: upload validation (MEDICAL_CERT vs
 * OTHER), download round-trip, the expiring-certs listing, and the reminder job's dedup +
 * idempotency logic.
 */
@SpringBootTest
@Testcontainers
class MemberDocumentServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MemberDocumentService memberDocumentService;
    @Autowired MemberService memberService;
    @Autowired MedicalCertReminderJob medicalCertReminderJob;

    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberDocumentRepository memberDocumentRepository;
    @Autowired MemberDocumentBlobRepository memberDocumentBlobRepository;
    @Autowired MedicalCertReminderLogRepository medicalCertReminderLogRepository;
    @Autowired EmailOutboxRepository emailOutboxRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void clean() {
        emailOutboxRepository.deleteAll();
        medicalCertReminderLogRepository.deleteAll();
        memberDocumentBlobRepository.deleteAll();
        memberDocumentRepository.deleteAll();
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

    private Gym gym(String name) {
        return gymRepository.save(Gym.builder().name(name).build());
    }

    private Long createMember(Gym gym, String email) {
        actAs(gym.getId(), "GYM_MANAGER");
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Test");
        pi.setLastName("Member");
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        return memberService.create(req).getId();
    }

    private MultipartFile pdfFile() {
        return new MockMultipartFile("file", "cert.pdf", "application/pdf", "hello world".getBytes());
    }

    @Test
    void medicalCert_withExpiryDate_isPersistedAndDownloadReturnsSameBytes() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "medcert@test.com");
        LocalDate expiry = LocalDate.now().plusMonths(6);

        MemberDocumentResponseDTO dto = memberDocumentService.upload(
                memberId, DocumentType.MEDICAL_CERT, null, expiry, pdfFile());

        assertThat(dto.getExpiryDate()).isEqualTo(expiry);
        assertThat(dto.getType()).isEqualTo("MEDICAL_CERT");

        MemberDocumentService.Download download = memberDocumentService.download(memberId, dto.getId());
        assertThat(download.content()).isEqualTo("hello world".getBytes());
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.filename()).isEqualTo("cert.pdf");
    }

    @Test
    void other_withoutTitle_isRejected() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "notitle@test.com");

        assertThatThrownBy(() -> memberDocumentService.upload(memberId, DocumentType.OTHER, null, null, pdfFile()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void other_withTitle_succeeds() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "withtitle@test.com");

        MemberDocumentResponseDTO dto = memberDocumentService.upload(
                memberId, DocumentType.OTHER, "Contratto firmato", null, pdfFile());

        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getTitle()).isEqualTo("Contratto firmato");
    }

    @Test
    void other_withExpiryDate_isIgnored() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "ignoreexpiry@test.com");

        MemberDocumentResponseDTO dto = memberDocumentService.upload(
                memberId, DocumentType.OTHER, "Documento", LocalDate.now().plusDays(10), pdfFile());

        assertThat(dto.getExpiryDate()).isNull();
    }

    @Test
    void other_multipleDocumentsPerMember_areAllPersisted() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "multi@test.com");

        memberDocumentService.upload(memberId, DocumentType.OTHER, "Doc 1", null, pdfFile());
        memberDocumentService.upload(memberId, DocumentType.OTHER, "Doc 2", null, pdfFile());
        memberDocumentService.upload(memberId, DocumentType.OTHER, "Doc 3", null, pdfFile());

        assertThat(memberDocumentService.findByMember(memberId)).hasSize(3);
    }

    @Test
    void findExpiring_onlyMedicalCertsWithinThreshold_scopedToGym() {
        Gym a = gym("Gym A");
        Gym b = gym("Gym B");

        Long memberA1 = createMember(a, "exp.soon@test.com");
        Long memberA2 = createMember(a, "exp.far@test.com");
        Long memberB = createMember(b, "exp.otherGym@test.com");

        actAs(a.getId(), "GYM_MANAGER");
        memberDocumentService.upload(memberA1, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(10), pdfFile());
        memberDocumentService.upload(memberA2, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(90), pdfFile());
        memberDocumentService.upload(memberA1, DocumentType.OTHER, "Non un certificato", null, pdfFile());

        actAs(b.getId(), "GYM_MANAGER");
        memberDocumentService.upload(memberB, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(5), pdfFile());

        actAs(a.getId(), "GYM_MANAGER");
        List<MedicalCertExpiringDTO> expiring = memberDocumentService.findExpiring(30);

        assertThat(expiring).hasSize(1);
        assertThat(expiring.get(0).getMemberId()).isEqualTo(memberA1);
    }

    @Test
    void reminderJob_isIdempotent_andOnlyConsidersMostRecentCertPerMember() {
        Gym a = gym("Gym A");
        Long memberId = createMember(a, "reminder@test.com");

        actAs(a.getId(), "GYM_MANAGER");
        // An older, already-superseded cert that also happens to fall in the window: must be
        // ignored in favour of the most recent one.
        Long olderDocId = memberDocumentService.upload(
                memberId, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(3), pdfFile()).getId();
        Long mostRecentDocId = memberDocumentService.upload(
                memberId, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(5), pdfFile()).getId();

        medicalCertReminderJob.sendReminders();
        long afterFirstRun = medicalCertReminderLogRepository.count();
        // A 5-day expiry falls within both the 30- and 7-day windows, but only the strictest
        // (smallest) applicable threshold is used — one reminder per night, not one per
        // threshold — so exactly one log row, against the most recent document, at "7".
        assertThat(afterFirstRun).isEqualTo(1);
        assertThat(medicalCertReminderLogRepository.findAll())
                .allSatisfy(log -> {
                    assertThat(log.getDocument().getId()).isEqualTo(mostRecentDocId);
                    assertThat(log.getDaysBefore()).isEqualTo(7);
                });
        assertThat(medicalCertReminderLogRepository.existsByDocumentIdAndDaysBefore(olderDocId, 30)).isFalse();
        assertThat(medicalCertReminderLogRepository.existsByDocumentIdAndDaysBefore(olderDocId, 7)).isFalse();

        medicalCertReminderJob.sendReminders();
        long afterSecondRun = medicalCertReminderLogRepository.count();
        assertThat(afterSecondRun).isEqualTo(afterFirstRun);
    }

    @Test
    void reminderJob_queuesIntoTheOutbox_withThreeLevelReplyTo() {
        // No gym.contactEmail: the reply-to must now fall back to the GYM_MANAGER's own
        // address (level 2) — the one observable behavior change of the outbox conversion,
        // since this job used to stop at level 1 and send nothing in reply-to at all here.
        Gym a = gym("Gym senza contact email");
        userRepository.save(AppUser.builder()
                .username("manager@gymA.test")
                .password("irrelevant")
                .role("GYM_MANAGER")
                .gym(a)
                .active(true)
                .build());
        Long memberId = createMember(a, "queued@test.com");
        memberDocumentService.upload(memberId, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(5), pdfFile());

        medicalCertReminderJob.sendReminders();

        List<EmailOutbox> queued = emailOutboxRepository.findAll();
        assertThat(queued).hasSize(1); // only the strictest threshold ("7") for a 5-day expiry
        assertThat(queued).allSatisfy(row -> {
            assertThat(row.getToAddress()).isEqualTo("queued@test.com");
            assertThat(row.getReplyTo()).isEqualTo("manager@gymA.test");
            assertThat(row.getDedupKey()).matches("cert:\\d+:7");
        });
    }
}
