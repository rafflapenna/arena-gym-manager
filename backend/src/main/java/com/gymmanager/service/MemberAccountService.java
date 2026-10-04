package com.gymmanager.service;

import com.gymmanager.dto.ChangePasswordRequestDTO;
import com.gymmanager.dto.MemberAccountResponseDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberAccountService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteService inviteService;
    private final CurrentUser currentUser;

    @Transactional
    public MemberAccountResponseDTO createAccount(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

        // Enforce gym scope: a user cannot create an account for a member of another gym.
        Long memberGymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(memberGymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }

        String email = member.getPersonalInfo().getEmail();

        if (userRepository.existsByUsernameIgnoreCase(email)) {
            throw new ConflictException("An account already exists for member with email: " + email);
        }

        AppUser user = AppUser.builder()
                .username(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("MEMBER")
                .active(true)
                .member(member)
                .gym(member.getGym())
                .firstName(member.getPersonalInfo().getFirstName())
                .lastName(member.getPersonalInfo().getLastName())
                .mustChangePassword(true)
                .build();
        userRepository.save(user);

        String inviteLink = inviteService.issueInvite(user);
        return new MemberAccountResponseDTO(email, inviteLink);
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequestDTO dto) {
        AppUser user = userRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for member id: " + memberId));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}
