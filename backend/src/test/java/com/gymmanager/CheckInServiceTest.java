package com.gymmanager;

import com.gymmanager.dto.CheckInResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.CheckIn;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.CheckInInsertService;
import com.gymmanager.service.CheckInService;
import com.gymmanager.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class CheckInServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CheckInService checkInService;
    @Autowired MemberService memberService;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CheckInRepository checkInRepository;
    @MockBean CheckInInsertService checkInInsertService;

    @BeforeEach
    void clean() {
        checkInRepository.deleteAll();
        memberRepository.deleteAll();
        // Default: delegate straight to the real repository, exactly like the real
        // CheckInInsertService would. @MockBean resets stubbing before every test, so tests
        // that need to exercise the race-recovery path re-stub this individually.
        when(checkInInsertService.insert(any(CheckIn.class)))
                .thenAnswer(invocation -> checkInRepository.save(invocation.getArgument(0)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

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
    void operatorCheckInIsIdempotentForTheDay() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym A").build());
        Long memberId = createMember(gym.getId(), "idem@test.com");

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        CheckInResponseDTO first = checkInService.checkIn(memberId, CheckInSource.RECEPTION);
        assertThat(first.isAlreadyCheckedIn()).isFalse();
        // No subscription seeded for this member -> non-blocking warning for the operator.
        assertThat(first.getOutcome()).isEqualTo("WARNING");

        CheckInResponseDTO second = checkInService.checkIn(memberId, CheckInSource.RECEPTION);
        assertThat(second.isAlreadyCheckedIn()).isTrue();
        assertThat(second.getId()).isEqualTo(first.getId());

        assertThat(checkInRepository.count()).isEqualTo(1);
    }

    @Test
    void selfCheckInBlockedWhenNotEligible() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym A").build());
        Long memberId = createMember(gym.getId(), "self@test.com");

        actAs(2L, memberId, gym.getId(), "MEMBER");
        assertThatThrownBy(() -> checkInService.checkIn(memberId, CheckInSource.SELF))
                .isInstanceOf(ConflictException.class);
        assertThat(checkInRepository.count()).isZero();
    }

    @Test
    void trainerAttendanceBackfillsCourseOnExistingReceptionCheckIn() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym A").build());
        Long memberId = createMember(gym.getId(), "backfill@test.com");

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        CheckInResponseDTO reception = checkInService.checkIn(memberId, CheckInSource.RECEPTION);
        assertThat(reception.isAlreadyCheckedIn()).isFalse();

        // Trainer later marks the same member present in a course: the pre-existing
        // reception entry must pick up the course attribution instead of being ignored.
        CheckInResponseDTO attended = checkInService.checkIn(memberId, CheckInSource.RECEPTION, 42L);
        assertThat(attended.isAlreadyCheckedIn()).isTrue();
        assertThat(attended.getId()).isEqualTo(reception.getId());
        assertThat(checkInRepository.count()).isEqualTo(1);
        assertThat(checkInRepository.findById(reception.getId()).orElseThrow().getTrainingClassId())
                .isEqualTo(42L);
    }

    @Test
    void secondCourseAttendanceDoesNotOverwriteExistingCourseAttribution() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym A").build());
        Long memberId = createMember(gym.getId(), "keep-existing@test.com");

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        checkInService.checkIn(memberId, CheckInSource.RECEPTION, 42L);

        // A different course's trainer marks the same member: the original attribution wins.
        CheckInResponseDTO second = checkInService.checkIn(memberId, CheckInSource.RECEPTION, 99L);
        assertThat(second.isAlreadyCheckedIn()).isTrue();
        assertThat(checkInRepository.findAll().get(0).getTrainingClassId()).isEqualTo(42L);
    }

    @Test
    void cannotCheckInMemberOfAnotherGym() {
        Gym a = gymRepository.save(Gym.builder().name("Gym A").build());
        Gym b = gymRepository.save(Gym.builder().name("Gym B").build());
        Long memberId = createMember(a.getId(), "scoped@test.com");

        // Instructor of gym B must not be able to check-in gym A's member (404).
        actAs(1L, null, b.getId(), "GYM_MANAGER");
        assertThatThrownBy(() -> checkInService.checkIn(memberId, CheckInSource.RECEPTION))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(checkInRepository.count()).isZero();
    }

    // ── Concurrent double-click race (round 3 follow-up) ─────────────────────

    @Test
    void concurrentRace_fallsBackToWinningEntryInsteadOfPropagatingException() {
        Gym gym = gymRepository.save(Gym.builder().name("Gym A").build());
        Long memberId = createMember(gym.getId(), "race@test.com");
        Member member = memberRepository.findById(memberId).orElseThrow();

        // Simulate another concurrent request committing its own row for the same
        // (member, day) in the exact window between this call's idempotency check (which
        // sees nothing yet) and its own insert attempt: the mocked CheckInInsertService is
        // the only path through which the real INSERT happens, so stubbing it lets us
        // pinpoint that window precisely, then report back the same failure the DB's
        // ux_check_ins_member_day unique index would raise for a real race.
        when(checkInInsertService.insert(any(CheckIn.class))).thenAnswer(invocation -> {
            checkInRepository.save(CheckIn.builder()
                    .member(member)
                    .gym(gym)
                    .checkInAt(LocalDateTime.now())
                    .source(CheckInSource.RECEPTION)
                    .build());
            throw new DataIntegrityViolationException("simulated concurrent race");
        });

        actAs(1L, null, gym.getId(), "GYM_MANAGER");
        CheckInResponseDTO result = checkInService.checkIn(memberId, CheckInSource.RECEPTION, 42L);

        assertThat(result.isAlreadyCheckedIn()).isTrue();
        assertThat(checkInRepository.count()).isEqualTo(1);
        // Same backfill logic as the sequential idempotency path: the race winner's entry
        // (created with no course context) picks up this call's trainingClassId.
        assertThat(checkInRepository.findAll().get(0).getTrainingClassId()).isEqualTo(42L);
    }
}
