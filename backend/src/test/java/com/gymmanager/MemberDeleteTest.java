package com.gymmanager;

import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.entity.DocumentType;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MedicalCertReminderLogRepository;
import com.gymmanager.repository.MemberDocumentBlobRepository;
import com.gymmanager.repository.MemberDocumentRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.CheckInService;
import com.gymmanager.service.MedicalCertReminderJob;
import com.gymmanager.service.MemberAccountService;
import com.gymmanager.service.MemberDocumentService;
import com.gymmanager.service.MemberService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Deleting a member must hard-delete its dependent records (app account + invite
 * tokens, check-ins, subscriptions, payments). Regression for the FK violation on
 * check_ins when deleting a member that had an enabled app account / a check-in.
 */
@SpringBootTest
@Testcontainers
class MemberDeleteTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MemberService memberService;
    @Autowired MemberAccountService memberAccountService;
    @Autowired CheckInService checkInService;
    @Autowired MemberDocumentService memberDocumentService;
    @Autowired MedicalCertReminderJob medicalCertReminderJob;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired UserRepository userRepository;
    @Autowired MemberDocumentRepository memberDocumentRepository;
    @Autowired MemberDocumentBlobRepository memberDocumentBlobRepository;
    @Autowired MedicalCertReminderLogRepository medicalCertReminderLogRepository;

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void actAs(Long userId, Long memberId, Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(userId, memberId, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Long createMember(Long gymId, String email) {
        actAs(1L, null, gymId, "GYM_MANAGER");
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

    @Test
    void deleteMember_withAccountAndCheckIn_succeeds() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym Del").build());
        Long memberId = createMember(gym.getId(), "del@test.com");

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        memberAccountService.createAccount(memberId);                 // login account + invite token
        checkInService.checkIn(memberId, CheckInSource.RECEPTION);    // a check-in row

        memberService.delete(memberId);

        assertThat(memberRepository.findById(memberId)).isEmpty();
        assertThat(checkInRepository.findByMemberIdWithDetails(memberId)).isEmpty();
        assertThat(userRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    void deleteMember_withDocumentsAndReminderLog_succeedsAndCleansUpBlobsAndLogs() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym Del Docs").build());
        Long memberId = createMember(gym.getId(), "deldocs@test.com");

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        MultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", "hello".getBytes());
        var doc = memberDocumentService.upload(
                memberId, DocumentType.MEDICAL_CERT, null, LocalDate.now().plusDays(5), file);
        String storageKey = memberDocumentRepository.findById(doc.getId()).orElseThrow().getStorageKey();
        medicalCertReminderJob.sendReminders(); // creates a MedicalCertReminderLog row FK'd to the document

        memberService.delete(memberId);

        assertThat(memberRepository.findById(memberId)).isEmpty();
        assertThat(memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(memberId)).isEmpty();
        assertThat(memberDocumentBlobRepository.findById(storageKey)).isEmpty();
        assertThat(medicalCertReminderLogRepository.existsByDocumentIdAndDaysBefore(doc.getId(), 30)).isFalse();
    }
}
