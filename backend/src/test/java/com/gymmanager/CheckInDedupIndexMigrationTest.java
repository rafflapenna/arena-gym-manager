package com.gymmanager;

import com.gymmanager.entity.CheckIn;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.CheckInRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the round-3 #4 dedup + unique-index migration statements in {@code schema.sql}
 * directly against a live Postgres instance, independent of {@code schema.sql}'s own execution
 * timing: on a freshly created test DB, {@code schema.sql} runs BEFORE Hibernate creates the
 * {@code check_ins} table ({@code defer-datasource-initialization=false}), so
 * {@code continue-on-error} always skips both statements on this test suite's single-startup
 * runs — same as several other {@code check_ins}-dependent statements already in
 * {@code schema.sql} (they only actually run on a 2nd app startup against a pre-existing DB,
 * i.e. dev/prod). This test executes the exact same SQL directly instead, once the table
 * exists, to prove the statements are both syntactically valid and semantically correct.
 */
@SpringBootTest
@Testcontainers
class CheckInDedupIndexMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired com.gymmanager.repository.FederationRepository federationRepository;
    @Autowired com.gymmanager.repository.FederationCardTypeRepository federationCardTypeRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CheckInRepository checkInRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired GymRepository gymRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;

    // Kept byte-for-byte in sync with schema.sql's "[Round 3 #4]" block.
    private static final String DEDUP_SQL = """
            DELETE FROM check_ins a
            USING check_ins b
            WHERE a.member_id = b.member_id
              AND a.check_in_at::date = b.check_in_at::date
              AND a.id <> b.id
              AND (
                    (a.training_class_id IS NULL AND b.training_class_id IS NOT NULL)
                    OR (a.training_class_id IS NOT DISTINCT FROM b.training_class_id AND a.id > b.id)
                  )
            """;

    private static final String UNIQUE_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_check_ins_member_day
                ON check_ins (member_id, (check_in_at::date))
            """;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS ux_check_ins_member_day");
        checkInRepository.deleteAll();
        memberRepository.deleteAll();
        // Per-gym subscription types seeded by SubscriptionTypeSeeder must go before the gyms.
        subscriptionTypeRepository.findAll().stream()
                .filter(t -> t.getGym() != null)
                .forEach(subscriptionTypeRepository::delete);
        federationCardTypeRepository.deleteAll();
        federationRepository.deleteAll();
        gymRepository.deleteAll();
    }

    private Long createMember(String email) {
        Gym gym = gymRepository.save(Gym.builder().name("Dedup Gym " + email).build());
        PersonalInfo pi = new PersonalInfo();
        pi.setFirstName("Dedup");
        pi.setLastName("Test");
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        Member member = Member.builder()
                .personalInfo(pi)
                .gym(gym)
                .registrationDate(LocalDate.now())
                .build();
        return memberRepository.save(member).getId();
    }

    private CheckIn insertCheckIn(Long memberId, Long gymId, LocalDateTime at, Long trainingClassId) {
        return checkInRepository.saveAndFlush(CheckIn.builder()
                .member(memberRepository.getReferenceById(memberId))
                .gym(gymRepository.getReferenceById(gymId))
                .checkInAt(at)
                .source(CheckInSource.RECEPTION)
                .trainingClassId(trainingClassId)
                .build());
    }

    @Test
    void dedupKeepsAttributedRow_thenUniqueIndexRejectsFurtherDuplicates() {
        Long memberId = createMember("dedup1@test.com");
        Long gymId = memberRepository.findById(memberId).orElseThrow().getGym().getId();
        LocalDateTime day = LocalDate.now().atTime(9, 0);

        insertCheckIn(memberId, gymId, day, null);
        CheckIn attributed = insertCheckIn(memberId, gymId, day.plusHours(1), 42L);
        insertCheckIn(memberId, gymId, day.plusHours(2), null);
        assertThat(checkInRepository.count()).isEqualTo(3);

        jdbcTemplate.execute(DEDUP_SQL);

        List<CheckIn> remaining = checkInRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(attributed.getId());
        assertThat(remaining.get(0).getTrainingClassId()).isEqualTo(42L);

        // Idempotent: a second run finds no more matching pairs.
        jdbcTemplate.execute(DEDUP_SQL);
        assertThat(checkInRepository.count()).isEqualTo(1);

        jdbcTemplate.execute(UNIQUE_INDEX_SQL);
        // Idempotent: re-creating an existing index is a no-op.
        jdbcTemplate.execute(UNIQUE_INDEX_SQL);

        assertThatThrownBy(() -> insertCheckIn(memberId, gymId, day.plusHours(3), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dedupKeepsEarliestRow_whenNeitherIsAttributed() {
        Long memberId = createMember("dedup2@test.com");
        Long gymId = memberRepository.findById(memberId).orElseThrow().getGym().getId();
        LocalDateTime day = LocalDate.now().atTime(9, 0);

        CheckIn earliest = insertCheckIn(memberId, gymId, day, null);
        insertCheckIn(memberId, gymId, day.plusHours(1), null);

        jdbcTemplate.execute(DEDUP_SQL);

        List<CheckIn> remaining = checkInRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(earliest.getId());
    }

    @Test
    void differentDays_areNotTouchedByDedup() {
        Long memberId = createMember("dedup3@test.com");
        Long gymId = memberRepository.findById(memberId).orElseThrow().getGym().getId();

        insertCheckIn(memberId, gymId, LocalDate.now().atTime(9, 0), null);
        insertCheckIn(memberId, gymId, LocalDate.now().plusDays(1).atTime(9, 0), null);

        jdbcTemplate.execute(DEDUP_SQL);

        assertThat(checkInRepository.count()).isEqualTo(2);
    }
}
