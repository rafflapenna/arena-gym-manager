package com.gymmanager.service;

import com.gymmanager.dto.ChangePasswordRequestDTO;
import com.gymmanager.dto.CreateInstructorRequestDTO;
import com.gymmanager.dto.CreateStaffRequestDTO;
import com.gymmanager.dto.CreateTrainerRequestDTO;
import com.gymmanager.dto.UserInviteResponseDTO;
import com.gymmanager.dto.UserResponseDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserManagementService {

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteService inviteService;
    private final CacheManager cacheManager;

    // ── ADMIN: create a gym manager for an existing gym ────────────────────
    // (endpoint path stays /users/instructors — see class Javadoc / roadmap §4 Piano B)
    @Transactional
    public UserInviteResponseDTO createInstructor(CreateInstructorRequestDTO dto, AuthPrincipal current) {
        assertCanCreateRole(current.role(), Roles.GYM_MANAGER);
        String email = normalizeEmail(dto.email());
        ensureUsernameFree(email);

        Gym gym = gymRepository.findById(dto.gymId())
                .orElseThrow(() -> new ResourceNotFoundException("Gym", dto.gymId()));

        if (!userRepository.findByGymIdAndRole(gym.getId(), Roles.GYM_MANAGER).isEmpty()) {
            throw new ConflictException("This gym already has a manager");
        }

        AppUser user = createUser(email, Roles.GYM_MANAGER, gym, dto.firstName(), dto.lastName());

        log.info("ADMIN created gym manager {} for gym '{}' (id={})", email, gym.getName(), gym.getId());
        return toInviteDTO(user, inviteService.issueInvite(user));
    }

    // ── GYM_MANAGER: create staff in own gym ───────────────────────────────
    @Transactional
    public UserInviteResponseDTO createStaff(CreateStaffRequestDTO dto, AuthPrincipal current) {
        assertCanCreateRole(current.role(), Roles.STAFF);
        Gym gym = requireGym(current);
        String email = normalizeEmail(dto.email());
        ensureUsernameFree(email);

        AppUser user = createUser(email, Roles.STAFF, gym, dto.firstName(), dto.lastName());

        log.info("Gym manager (gym id={}) created staff {}", gym.getId(), email);
        return toInviteDTO(user, inviteService.issueInvite(user));
    }

    // ── GYM_MANAGER: create a trainer in own gym ───────────────────────────
    @Transactional
    public UserInviteResponseDTO createTrainer(CreateTrainerRequestDTO dto, AuthPrincipal current) {
        assertCanCreateRole(current.role(), Roles.TRAINER);
        Gym gym = requireGym(current);
        String email = normalizeEmail(dto.email());
        ensureUsernameFree(email);

        AppUser user = createUser(email, Roles.TRAINER, gym, dto.firstName(), dto.lastName());

        log.info("Gym manager (gym id={}) created trainer {}", gym.getId(), email);
        return toInviteDTO(user, inviteService.issueInvite(user));
    }

    // ── Listing ───────────────────────────────────────────────────────────
    public List<UserResponseDTO> listInstructors() {
        return userRepository.findByRole(Roles.GYM_MANAGER).stream().map(this::toDTO).toList();
    }

    /** Staff of the current user's gym (gym manager view). */
    public List<UserResponseDTO> listStaffOfCurrentGym(AuthPrincipal current) {
        Long gymId = requireGym(current).getId();
        return userRepository.findByGymIdAndRole(gymId, Roles.STAFF).stream().map(this::toDTO).toList();
    }

    /** Trainers of the current user's gym (gym manager view). */
    public List<UserResponseDTO> listTrainersOfCurrentGym(AuthPrincipal current) {
        Long gymId = requireGym(current).getId();
        return userRepository.findByGymIdAndRole(gymId, Roles.TRAINER).stream().map(this::toDTO).toList();
    }

    /**
     * Users of the current gym that can be assigned as a course's teacher: the TRAINERs plus
     * the GYM_MANAGER, who in a small gym typically teaches too. Deliberately a separate list
     * from {@link #listTrainersOfCurrentGym} — that one backs the "Trainers" tab of the user
     * management screen, where a gym manager does not belong.
     */
    public List<UserResponseDTO> listTeachersOfCurrentGym(AuthPrincipal current) {
        Long gymId = requireGym(current).getId();
        return userRepository
                .findByGymIdAndRoleInOrderByLastNameAscFirstNameAsc(gymId, List.of(Roles.TRAINER, Roles.GYM_MANAGER))
                .stream().map(this::toDTO).toList();
    }

    // ── Update name / email ───────────────────────────────────────────────
    @Transactional
    public UserResponseDTO updateUser(Long userId, com.gymmanager.dto.UpdateUserRequestDTO dto, AuthPrincipal current) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertCanManage(target, current);

        target.setFirstName(dto.firstName().trim());
        target.setLastName(dto.lastName().trim());

        if (dto.email() != null && !dto.email().isBlank()) {
            String email = normalizeEmail(dto.email());
            if (!email.equals(target.getUsername())) {
                ensureUsernameFree(email);
                String oldUsername = target.getUsername();
                target.setUsername(email);
                syncLinkedMemberEmail(target, email);
                // Evict both: the old username must stop resolving to this (now renamed)
                // account, and the new one must not keep serving a pre-existing cache entry
                // (e.g. from a prior account that used to have this username) instead of a
                // fresh lookup for the renamed one.
                evictUserDetailsCache(oldUsername);
                evictUserDetailsCache(email);
            }
        }
        return toDTO(userRepository.save(target));
    }

    /**
     * Keeps a linked Member's {@code personalInfo.email} in sync when a MEMBER account's
     * login email is changed here — the inverse of {@code MemberService.syncLinkedAccountUsername}.
     * Without this, editing a member's login email from the user-management screen would leave
     * the member's own profile (and anything reading it) pointing at the stale address.
     */
    private void syncLinkedMemberEmail(AppUser user, String newEmail) {
        Member member = user.getMember();
        if (member == null) {
            return;
        }
        if (memberRepository.existsByPersonalInfoEmailAndIdNot(newEmail, member.getId())) {
            throw new ConflictException("A member with email already exists: " + newEmail);
        }
        member.getPersonalInfo().setEmail(newEmail);
        memberRepository.save(member);
        log.info("Synced member id={} personalInfo.email -> {}", member.getId(), newEmail);
    }

    // ── Reset password (re-issue invite link) ─────────────────────────────
    @Transactional
    public UserInviteResponseDTO resetPassword(Long userId, AuthPrincipal current) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertCanManage(target, current);

        target.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        target.setMustChangePassword(true);
        userRepository.save(target);

        log.info("Password reset (re-invite) for {}", target.getUsername());
        return toInviteDTO(target, inviteService.issueInvite(target));
    }

    // ── Self-service: change own password (any authenticated role) ─────────
    @Transactional
    public void changeOwnPassword(AuthPrincipal current, ChangePasswordRequestDTO dto) {
        AppUser user = userRepository.findById(current.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", current.userId()));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    // ── Enable / disable ──────────────────────────────────────────────────
    @Transactional
    public UserResponseDTO setActive(Long userId, boolean active, AuthPrincipal current) {
        AppUser target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        assertCanManage(target, current);
        target.setActive(active);
        UserResponseDTO result = toDTO(userRepository.save(target));
        // A just-deactivated account must stop being able to authenticate immediately, not
        // after the cache's 60s TTL elapses — the JWT filter and the login provider both
        // resolve UserDetails through this same cache.
        evictUserDetailsCache(target.getUsername());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Evicts a single username's cached {@code UserDetails} (see CacheConfig /
     *  UserDetailsServiceImpl) so a just-deactivated or renamed account can't keep
     *  authenticating off a stale cache entry for up to the 60s TTL. */
    private void evictUserDetailsCache(String username) {
        Cache cache = cacheManager.getCache("userDetails");
        if (cache != null) {
            cache.evict(username);
        }
    }

    /**
     * Enforces the creation-permission matrix:
     * ADMIN → GYM_MANAGER only, GYM_MANAGER → STAFF or TRAINER, STAFF → MEMBER only,
     * TRAINER → nothing. Any other combination throws 403.
     *
     * <p>⚠️ Compatibility window (roadmap §4): {@code current.role()} comes from the JWT claim,
     * which may still carry the legacy "INSTRUCTOR" value for tokens issued before the
     * INSTRUCTOR→GYM_MANAGER rename. The route-level checks (hasRole(...)) are unaffected
     * because those re-derive authorities from the DB on every request, but this
     * claim-based switch would otherwise 403 a valid gym manager for up to ~8h after
     * deploy. Remove the "INSTRUCTOR" case in a follow-up cleanup PR once old tokens expire.
     */
    private void assertCanCreateRole(String creatorRole, String targetRole) {
        boolean allowed = switch (creatorRole) {
            case Roles.ADMIN -> Roles.GYM_MANAGER.equals(targetRole);
            case Roles.GYM_MANAGER, "INSTRUCTOR" ->
                    Roles.STAFF.equals(targetRole) || Roles.TRAINER.equals(targetRole);
            case Roles.STAFF -> Roles.MEMBER.equals(targetRole);
            default -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Role '" + creatorRole + "' is not allowed to create accounts with role '" + targetRole + "'");
        }
    }

    private AppUser createUser(String email, String role, Gym gym, String firstName, String lastName) {
        AppUser user = AppUser.builder()
                .username(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(role)
                .active(true)
                .gym(gym)
                .firstName(firstName != null ? firstName.trim() : null)
                .lastName(lastName != null ? lastName.trim() : null)
                .mustChangePassword(true)
                .build();
        return userRepository.save(user);
    }

    private void ensureUsernameFree(String email) {
        if (userRepository.existsByUsernameIgnoreCase(email)) {
            throw new ConflictException("An account already exists with email: " + email);
        }
    }

    private Gym requireGym(AuthPrincipal current) {
        if (current.gymId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not associated to a gym");
        }
        return gymRepository.findById(current.gymId())
                .orElseThrow(() -> new ResourceNotFoundException("Gym", current.gymId()));
    }

    /** ADMIN manages anyone; instructor manages only users in own gym. */
    private void assertCanManage(AppUser target, AuthPrincipal current) {
        if (current.isAdmin()) return;
        Long targetGymId = target.getGym() != null ? target.getGym().getId() : null;
        if (targetGymId == null || !targetGymId.equals(current.gymId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot manage a user outside your gym");
        }
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    private UserResponseDTO toDTO(AppUser u) {
        return UserResponseDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(u.getRole())
                .active(u.isActive())
                .mustChangePassword(u.isMustChangePassword())
                .gymId(u.getGym() != null ? u.getGym().getId() : null)
                .gymName(u.getGym() != null ? u.getGym().getName() : null)
                .memberId(u.getMember() != null ? u.getMember().getId() : null)
                .build();
    }

    private UserInviteResponseDTO toInviteDTO(AppUser u, String inviteLink) {
        return new UserInviteResponseDTO(
                u.getId(),
                u.getUsername(),
                u.getRole(),
                u.getGym() != null ? u.getGym().getId() : null,
                u.getGym() != null ? u.getGym().getName() : null,
                inviteLink
        );
    }
}
