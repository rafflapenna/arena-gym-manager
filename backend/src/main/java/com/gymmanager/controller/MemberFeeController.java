package com.gymmanager.controller;

import com.gymmanager.dto.MemberFederationCardRequestDTO;
import com.gymmanager.dto.MemberFederationCardResponseDTO;
import com.gymmanager.dto.MemberFeeRequestDTO;
import com.gymmanager.dto.MemberFeeResponseDTO;
import com.gymmanager.service.MemberFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Charging the membership and federation fees. STAFF can charge them (it is desk work); only
 * the amounts in the fee lists are manager-only, and those live in FederationController.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberFeeController {

    private final MemberFeeService memberFeeService;

    @GetMapping("/members/{memberId}/fees")
    public List<MemberFeeResponseDTO> findByMember(@PathVariable Long memberId) {
        return memberFeeService.findByMember(memberId);
    }

    @PostMapping("/members/{memberId}/fees")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberFeeResponseDTO charge(
            @PathVariable Long memberId, @Valid @RequestBody MemberFeeRequestDTO dto) {
        return memberFeeService.charge(memberId, dto);
    }

    @DeleteMapping("/fees/{feeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long feeId) {
        memberFeeService.delete(feeId);
    }

    /** Renewal reminders, same shape as /subscriptions/expiring. */
    @GetMapping("/fees/expiring")
    public List<MemberFeeResponseDTO> findExpiring(@RequestParam(defaultValue = "30") int days) {
        return memberFeeService.findExpiring(days);
    }

    /** The member's card number with each federation — fixed, unlike the yearly fee. */
    @GetMapping("/members/{memberId}/federation-cards")
    public List<MemberFederationCardResponseDTO> findCards(@PathVariable Long memberId) {
        return memberFeeService.findCards(memberId);
    }

    @PutMapping("/members/{memberId}/federation-cards/{federationId}")
    public MemberFederationCardResponseDTO saveCard(
            @PathVariable Long memberId, @PathVariable Long federationId,
            @Valid @RequestBody MemberFederationCardRequestDTO dto) {
        return memberFeeService.saveCard(memberId, federationId, dto);
    }

    @DeleteMapping("/members/{memberId}/federation-cards/{federationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@PathVariable Long memberId, @PathVariable Long federationId) {
        memberFeeService.deleteCard(memberId, federationId);
    }
}
