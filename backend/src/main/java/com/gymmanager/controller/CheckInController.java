package com.gymmanager.controller;

import com.gymmanager.dto.CheckInResponseDTO;
import com.gymmanager.dto.QrCheckInRequestDTO;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Operator-facing attendance endpoints (STAFF/GYM_MANAGER). Member self check-in
 * lives on {@code MeController} under {@code /me/check-ins}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/members/{memberId}/check-ins")
    public ResponseEntity<CheckInResponseDTO> checkIn(@PathVariable Long memberId) {
        CheckInResponseDTO dto = checkInService.checkIn(memberId, CheckInSource.RECEPTION);
        HttpStatus status = dto.isAlreadyCheckedIn() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(dto);
    }

    @GetMapping("/members/{memberId}/check-ins")
    public List<CheckInResponseDTO> findByMember(@PathVariable Long memberId) {
        return checkInService.findByMember(memberId);
    }

    /**
     * QR check-in (roadmap #5): registers the attendance of the member behind the scanned
     * token. Same status semantics as the reception check-in: 201 for a new entry, 200 when
     * the member had already checked in today ({@code alreadyCheckedIn}).
     */
    @PostMapping("/check-ins/qr")
    public ResponseEntity<CheckInResponseDTO> checkInByQr(@Valid @RequestBody QrCheckInRequestDTO dto) {
        CheckInResponseDTO checkIn = checkInService.checkInByToken(dto.token());
        HttpStatus status = checkIn.isAlreadyCheckedIn() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(checkIn);
    }

    @GetMapping("/check-ins")
    public List<CheckInResponseDTO> findByDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return checkInService.findByGymOnDate(date != null ? date : LocalDate.now());
    }
}
