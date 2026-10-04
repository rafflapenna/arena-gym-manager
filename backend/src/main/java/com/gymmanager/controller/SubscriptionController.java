package com.gymmanager.controller;

import com.gymmanager.dto.SubscriptionRequestDTO;
import com.gymmanager.dto.SubscriptionResponseDTO;
import com.gymmanager.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/members/{memberId}/subscriptions")
    public ResponseEntity<SubscriptionResponseDTO> create(
            @PathVariable Long memberId,
            @Valid @RequestBody SubscriptionRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.create(memberId, dto));
    }

    @GetMapping("/members/{memberId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> findByMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(subscriptionService.findByMember(memberId));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> findAll(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(subscriptionService.findAll(status));
    }

    @GetMapping("/subscriptions/expiring")
    public ResponseEntity<List<SubscriptionResponseDTO>> findExpiring(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(subscriptionService.findExpiring(days));
    }

    @PatchMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<SubscriptionResponseDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.cancel(id));
    }
}
