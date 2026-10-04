package com.gymmanager.controller;

import com.gymmanager.dto.QrTokenResponseDTO;
import com.gymmanager.service.CheckinTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Backoffice access to a member's QR identity card token (roadmap #5): STAFF/GYM_MANAGER
 * retrieve it for printing/handing out, or rotate it when a card is lost or compromised.
 * Scoped to the operator's own gym by {@link CheckinTokenService} (cross-gym id -> 404).
 */
@RestController
@RequestMapping("/api/v1/members/{id}/qr")
@RequiredArgsConstructor
public class MemberQrController {

    private final CheckinTokenService checkinTokenService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','GYM_MANAGER')")
    public QrTokenResponseDTO getToken(@PathVariable Long id) {
        return new QrTokenResponseDTO(checkinTokenService.getToken(id));
    }

    @PostMapping("/rotate")
    @PreAuthorize("hasAnyRole('STAFF','GYM_MANAGER')")
    public QrTokenResponseDTO rotate(@PathVariable Long id) {
        return new QrTokenResponseDTO(checkinTokenService.rotate(id));
    }
}
