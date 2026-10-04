package com.gymmanager.controller;

import com.gymmanager.dto.MemberAccountResponseDTO;
import com.gymmanager.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberAccountController {

    private final MemberAccountService memberAccountService;

    @PostMapping("/{id}/account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STAFF','GYM_MANAGER')")
    public MemberAccountResponseDTO createAccount(@PathVariable Long id) {
        return memberAccountService.createAccount(id);
    }
}
