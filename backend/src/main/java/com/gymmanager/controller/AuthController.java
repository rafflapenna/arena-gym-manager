package com.gymmanager.controller;

import com.gymmanager.dto.AuthResponseDTO;
import com.gymmanager.dto.ForgotPasswordRequestDTO;
import com.gymmanager.dto.LoginRequestDTO;
import com.gymmanager.dto.SetPasswordRequestDTO;
import com.gymmanager.entity.AppUser;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.security.ClientIpResolver;
import com.gymmanager.security.JwtUtil;
import com.gymmanager.security.LoginAttemptService;
import com.gymmanager.service.InviteService;
import com.gymmanager.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final InviteService inviteService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        String ip = ClientIpResolver.clientIp(httpRequest);
        loginAttemptService.assertNotBlocked(request.username(), ip);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (AuthenticationException e) {
            loginAttemptService.onFailure(request.username(), ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        loginAttemptService.onSuccess(request.username(), ip);

        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("");

        AppUser appUser = userRepository.findByUsername(username).orElse(null);
        Long userId = appUser != null ? appUser.getId() : null;
        Long memberId = (appUser != null && appUser.getMember() != null) ? appUser.getMember().getId() : null;
        Long gymId = (appUser != null && appUser.getGym() != null) ? appUser.getGym().getId() : null;
        boolean mustChangePassword = appUser != null && appUser.isMustChangePassword();
        String name = displayName(appUser, username);

        String token = jwtUtil.generateToken(username, role, userId, memberId, gymId, name, mustChangePassword);
        return new AuthResponseDTO(token, username, name, role, memberId, gymId, mustChangePassword);
    }

    @PostMapping("/set-password")
    public AuthResponseDTO setPassword(@Valid @RequestBody SetPasswordRequestDTO request) {
        AppUser user = inviteService.redeemInvite(request.token(), request.newPassword());

        Long memberId = user.getMember() != null ? user.getMember().getId() : null;
        Long gymId = user.getGym() != null ? user.getGym().getId() : null;
        String name = displayName(user, user.getUsername());
        String jwtToken = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getId(), memberId, gymId, name, false);
        return new AuthResponseDTO(jwtToken, user.getUsername(), name, user.getRole(), memberId, gymId, false);
    }

    /**
     * Self-service password recovery. Always returns 204 No Content regardless of
     * whether the email exists, to prevent user enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO dto) {
        passwordRecoveryService.requestReset(dto.email());
        return ResponseEntity.noContent().build();
    }

    /** Full name from first/last name, falling back to the username. */
    private static String displayName(AppUser user, String fallback) {
        if (user == null) return fallback;
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? fallback : full;
    }
}
