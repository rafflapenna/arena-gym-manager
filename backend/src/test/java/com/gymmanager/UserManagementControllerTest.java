package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.ChangePasswordRequestDTO;
import com.gymmanager.dto.UpdateUserRequestDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PersonalInfo;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.security.UserDetailsServiceImpl;
import com.gymmanager.service.UserManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserManagementControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired com.gymmanager.repository.FederationRepository federationRepository;
    @Autowired com.gymmanager.repository.FederationCardTypeRepository federationCardTypeRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GymRepository gymRepository;
    @Autowired UserRepository userRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserManagementService userManagementService;
    @Autowired UserDetailsServiceImpl userDetailsServiceImpl;
    @Autowired CacheManager cacheManager;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        // Order: users first (FK to gym and to member), then members (FK to gym), then the
        // per-gym subscription types seeded by SubscriptionTypeSeeder for the DataInitializer
        // default gym, then gyms.
        userRepository.deleteAll();
        memberRepository.deleteAll();
        subscriptionTypeRepository.findAll().stream()
                .filter(t -> t.getGym() != null)
                .forEach(subscriptionTypeRepository::delete);
        federationCardTypeRepository.deleteAll();
        federationRepository.deleteAll();
        gymRepository.deleteAll();
    }

    private AppUser createUser(String role, Gym gym, String rawPassword) {
        return userRepository.save(AppUser.builder()
                .username(role.toLowerCase() + "@test.com")
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(true)
                .gym(gym)
                .mustChangePassword(true)
                .build());
    }

    private RequestPostProcessor as(AppUser user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        auth.setDetails(new AuthPrincipal(user.getId(), null,
                user.getGym() != null ? user.getGym().getId() : null, user.getRole()));
        return authentication(auth);
    }

    private AuthPrincipal adminPrincipal() {
        return new AuthPrincipal(1L, null, null, "ADMIN");
    }

    // ── POST /api/v1/users/me/password ──────────────────────────────────

    @Test
    void changeOwnPassword_staff_succeeds() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Pwd Gym").build());
        AppUser user = createUser("STAFF", gym, "oldPass123");

        String body = objectMapper.writeValueAsString(new ChangePasswordRequestDTO("oldPass123", "newPass456"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        AppUser reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newPass456", reloaded.getPassword())).isTrue();
        assertThat(reloaded.isMustChangePassword()).isFalse();
    }

    @Test
    void changeOwnPassword_gymManager_succeeds() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Pwd Gym 2").build());
        AppUser user = createUser("GYM_MANAGER", gym, "oldPass123");

        String body = objectMapper.writeValueAsString(new ChangePasswordRequestDTO("oldPass123", "newPass456"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        AppUser reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newPass456", reloaded.getPassword())).isTrue();
    }

    @Test
    void changeOwnPassword_wrongCurrentPassword_returns400() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Pwd Gym 3").build());
        AppUser user = createUser("STAFF", gym, "oldPass123");

        String body = objectMapper.writeValueAsString(new ChangePasswordRequestDTO("wrongPass", "newPass456"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeOwnPassword_noAuth_returns403() throws Exception {
        // No custom AuthenticationEntryPoint is configured, so an unauthenticated request to
        // any protected endpoint is rejected with Spring Security's default 403 (not 401) —
        // pre-existing, app-wide behavior, unrelated to this endpoint.
        String body = objectMapper.writeValueAsString(new ChangePasswordRequestDTO("a", "bbbbbb"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/users/trainers ──────────────────────────────────────

    @Test
    void listTrainers_asStaff_returns200() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Trainer Gym").build());
        AppUser staff = createUser("STAFF", gym, "pass123456");

        mockMvc.perform(get("/api/v1/users/trainers").with(as(staff)))
                .andExpect(status().isOk());
    }

    // ── userDetails cache eviction on deactivate / rename ───────────────────

    @Test
    void deactivate_evictsUserDetailsCache() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Evict Gym").build());
        AppUser target = createUser("STAFF", gym, "pass123456");

        // Warm the cache the same way the JWT filter / login provider would.
        userDetailsServiceImpl.loadUserByUsername(target.getUsername());
        assertThat(cacheManager.getCache("userDetails").get(target.getUsername())).isNotNull();

        userManagementService.setActive(target.getId(), false, adminPrincipal());

        assertThat(cacheManager.getCache("userDetails").get(target.getUsername())).isNull();
        // Without the eviction this would still succeed for up to the cache's 60s TTL.
        assertThatThrownBy(() -> userDetailsServiceImpl.loadUserByUsername(target.getUsername()))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void updateUser_emailChange_evictsOldUsernameCache() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Evict Gym 2").build());
        AppUser target = createUser("STAFF", gym, "pass123456");
        String oldUsername = target.getUsername();
        String newEmail = "renamed-" + UUID.randomUUID() + "@test.com";

        userDetailsServiceImpl.loadUserByUsername(oldUsername);
        assertThat(cacheManager.getCache("userDetails").get(oldUsername)).isNotNull();

        userManagementService.updateUser(target.getId(),
                new UpdateUserRequestDTO("First", "Last", newEmail), adminPrincipal());

        assertThat(cacheManager.getCache("userDetails").get(oldUsername)).isNull();
    }

    // ── updateUser syncs linked Member.personalInfo.email ───────────────────

    @Test
    void updateUser_memberAccount_emailChange_syncsLinkedMemberEmail() throws Exception {
        Gym gym = gymRepository.save(Gym.builder().name("Sync Gym").build());

        PersonalInfo pi = new PersonalInfo();
        pi.setFirstName("Mario");
        pi.setLastName("Rossi");
        pi.setEmail("mario.old-" + UUID.randomUUID() + "@test.com");
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        Member member = memberRepository.save(Member.builder()
                .personalInfo(pi)
                .gym(gym)
                .registrationDate(LocalDate.now())
                .build());

        AppUser memberUser = userRepository.save(AppUser.builder()
                .username(pi.getEmail())
                .password(passwordEncoder.encode("pass123456"))
                .role("MEMBER")
                .active(true)
                .gym(gym)
                .member(member)
                .mustChangePassword(true)
                .build());

        String newEmail = "mario.new-" + UUID.randomUUID() + "@test.com";
        userManagementService.updateUser(memberUser.getId(),
                new UpdateUserRequestDTO("Mario", "Rossi", newEmail), adminPrincipal());

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getPersonalInfo().getEmail()).isEqualTo(newEmail);
        assertThat(userRepository.findById(memberUser.getId()).orElseThrow().getUsername()).isEqualTo(newEmail);
    }
}
