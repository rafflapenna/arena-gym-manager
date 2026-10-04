package com.gymmanager;

import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.MemberService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MultiTenantScopingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MemberService memberService;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;

    @BeforeEach
    void clean() {
        // Only members; gyms are referenced by seeded users (FK), and gym-scoped
        // counts ignore gyms without members anyway.
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

    private MemberRequestDTO request(String first, String last, String email) {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName(first);
        pi.setLastName(last);
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        return req;
    }

    private int countVisibleMembers() {
        return memberService.findAll(null, null, 0, 50, "lastName", "asc").getContent().size();
    }

    @Test
    void membersAreIsolatedPerGym() {
        Gym a = gymRepository.save(Gym.builder().name("Gym A").build());
        Gym b = gymRepository.save(Gym.builder().name("Gym B").build());

        // Instructor of gym A creates a member -> visible only to gym A
        actAs(a.getId(), "GYM_MANAGER");
        memberService.create(request("Mario", "A", "mario.a@test.com"));
        assertThat(countVisibleMembers()).isEqualTo(1);

        // Instructor of gym B sees none of A's members, then creates one of its own
        actAs(b.getId(), "GYM_MANAGER");
        assertThat(countVisibleMembers()).isZero();
        memberService.create(request("Luigi", "B", "luigi.b@test.com"));
        assertThat(countVisibleMembers()).isEqualTo(1);

        // ADMIN (no gym) sees everything across tenants
        actAs(null, "ADMIN");
        assertThat(countVisibleMembers()).isEqualTo(2);
    }

    @Test
    void cannotReadAMemberOfAnotherGym() {
        Gym a = gymRepository.save(Gym.builder().name("Gym A").build());
        Gym b = gymRepository.save(Gym.builder().name("Gym B").build());

        actAs(a.getId(), "GYM_MANAGER");
        Long memberId = memberService.create(request("Mario", "A", "mario.a@test.com")).getId();

        // Instructor of gym B must not be able to fetch gym A's member (404)
        actAs(b.getId(), "GYM_MANAGER");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> memberService.findById(memberId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
