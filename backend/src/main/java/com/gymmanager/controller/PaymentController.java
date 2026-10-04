package com.gymmanager.controller;

import com.gymmanager.dto.PaymentRequestDTO;
import com.gymmanager.dto.PaymentResponseDTO;
import com.gymmanager.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/subscriptions/{subscriptionId}/payments")
    public ResponseEntity<PaymentResponseDTO> create(
            @PathVariable Long subscriptionId,
            @Valid @RequestBody PaymentRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(subscriptionId, dto));
    }

    @GetMapping("/subscriptions/{subscriptionId}/payments")
    public ResponseEntity<List<PaymentResponseDTO>> findBySubscription(@PathVariable Long subscriptionId) {
        return ResponseEntity.ok(paymentService.findBySubscription(subscriptionId));
    }

    @GetMapping("/members/{memberId}/payments")
    public ResponseEntity<List<PaymentResponseDTO>> findByMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(paymentService.findByMember(memberId));
    }
}
