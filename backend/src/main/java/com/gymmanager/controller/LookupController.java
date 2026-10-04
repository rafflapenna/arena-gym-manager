package com.gymmanager.controller;

import com.gymmanager.dto.BeltGradeRequestDTO;
import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.PaymentMethodRequestDTO;
import com.gymmanager.dto.SubscriptionTypeRequestDTO;
import com.gymmanager.security.CurrentUser;
import com.gymmanager.service.LookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LookupController {

    private final LookupService lookupService;
    private final CurrentUser currentUser;

    /** Subscription types are per-gym; ADMIN (no gym) sees every row, including legacy ones. */
    @GetMapping("/subscription-types")
    public List<LookupDTO> getSubscriptionTypes() {
        return lookupService.getSubscriptionTypes(currentUser.isAdmin() ? null : currentUser.gymId());
    }

    @PostMapping("/subscription-types")
    public ResponseEntity<LookupDTO> createSubscriptionType(@Valid @RequestBody SubscriptionTypeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lookupService.createSubscriptionType(dto, currentUser.gymId()));
    }

    /** Updates label/months/defaultPrice of a subscription type; {@code code} is immutable (see
     *  {@link LookupService#updateSubscriptionType}). Same authorization as the POST above. */
    @PutMapping("/subscription-types/{id}")
    public LookupDTO updateSubscriptionType(@PathVariable Long id, @Valid @RequestBody SubscriptionTypeRequestDTO dto) {
        return lookupService.updateSubscriptionType(id, dto, currentUser.gymId());
    }

    /** ADMIN-only (route-level, see SecurityConfig); see {@link LookupService#deleteSubscriptionType}. */
    @DeleteMapping("/subscription-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubscriptionType(@PathVariable Long id) {
        lookupService.deleteSubscriptionType(id);
    }

    @GetMapping("/subscription-statuses")
    public List<LookupDTO> getSubscriptionStatuses() {
        return lookupService.getSubscriptionStatuses();
    }

    @GetMapping("/payment-methods")
    public List<LookupDTO> getPaymentMethods() {
        return lookupService.getPaymentMethods();
    }

    @PostMapping("/payment-methods")
    public ResponseEntity<LookupDTO> createPaymentMethod(@Valid @RequestBody PaymentMethodRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lookupService.createPaymentMethod(dto));
    }

    @GetMapping("/belt-grades")
    public List<LookupDTO> getBeltGrades() {
        return lookupService.getBeltGrades();
    }

    @PostMapping("/belt-grades")
    public ResponseEntity<LookupDTO> createBeltGrade(@Valid @RequestBody BeltGradeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lookupService.createBeltGrade(dto));
    }
}
