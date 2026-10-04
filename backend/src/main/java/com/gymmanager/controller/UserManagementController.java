package com.gymmanager.controller;

import com.gymmanager.dto.ChangePasswordRequestDTO;
import com.gymmanager.dto.CreateInstructorRequestDTO;
import com.gymmanager.dto.CreateStaffRequestDTO;
import com.gymmanager.dto.CreateTrainerRequestDTO;
import com.gymmanager.dto.UpdateUserRequestDTO;
import com.gymmanager.dto.UserInviteResponseDTO;
import com.gymmanager.dto.UserResponseDTO;
import com.gymmanager.security.CurrentUser;
import com.gymmanager.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final CurrentUser currentUser;

    // ── Gym managers (ADMIN) — path stays /instructors, see roadmap §4 Piano B ─
    @PostMapping("/instructors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserInviteResponseDTO createInstructor(@Valid @RequestBody CreateInstructorRequestDTO dto) {
        return userManagementService.createInstructor(dto, currentUser.get());
    }

    @GetMapping("/instructors")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponseDTO> listInstructors() {
        return userManagementService.listInstructors();
    }

    // ── Staff (GYM_MANAGER, in own gym) ────────────────────────────────────
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public UserInviteResponseDTO createStaff(@Valid @RequestBody CreateStaffRequestDTO dto) {
        return userManagementService.createStaff(dto, currentUser.get());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public List<UserResponseDTO> listStaff() {
        return userManagementService.listStaffOfCurrentGym(currentUser.get());
    }

    // ── Trainers (GYM_MANAGER, in own gym) ─────────────────────────────────
    @PostMapping("/trainers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GYM_MANAGER')")
    public UserInviteResponseDTO createTrainer(@Valid @RequestBody CreateTrainerRequestDTO dto) {
        return userManagementService.createTrainer(dto, currentUser.get());
    }

    @GetMapping("/trainers")
    @PreAuthorize("hasAnyRole('GYM_MANAGER','STAFF')")
    public List<UserResponseDTO> listTrainers() {
        return userManagementService.listTrainersOfCurrentGym(currentUser.get());
    }

    // ── Assignable course teachers (TRAINERs + the GYM_MANAGER) ────────────
    // Separate from /trainers on purpose: /trainers backs the user-management "Trainers"
    // tab (where a gym manager must not appear), this one backs the course form's
    // "Insegnante" picker, where a gym manager who also teaches must appear.
    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('GYM_MANAGER','STAFF')")
    public List<UserResponseDTO> listTeachers() {
        return userManagementService.listTeachersOfCurrentGym(currentUser.get());
    }

    // ── Self-service: change own password (any authenticated role) ─────────
    // Role-agnostic counterpart to MeController's /me/password (MEMBER-only): the FE
    // uses this single endpoint for every role's "change password" screen.
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        userManagementService.changeOwnPassword(currentUser.get(), dto);
    }

    // ── Update name / email (ADMIN or GYM_MANAGER for own gym) ─────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GYM_MANAGER')")
    public UserResponseDTO update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequestDTO dto) {
        return userManagementService.updateUser(id, dto, currentUser.get());
    }

    // ── Reset password: re-issue a set-password invite link ────────────────
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN','GYM_MANAGER')")
    public UserInviteResponseDTO resetPassword(@PathVariable Long id) {
        return userManagementService.resetPassword(id, currentUser.get());
    }

    // ── Enable / disable (ADMIN or GYM_MANAGER for own gym) ────────────────
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','GYM_MANAGER')")
    public UserResponseDTO activate(@PathVariable Long id) {
        return userManagementService.setActive(id, true, currentUser.get());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','GYM_MANAGER')")
    public UserResponseDTO deactivate(@PathVariable Long id) {
        return userManagementService.setActive(id, false, currentUser.get());
    }
}
