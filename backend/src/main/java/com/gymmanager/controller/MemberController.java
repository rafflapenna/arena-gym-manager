package com.gymmanager.controller;

import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.MemberResponseDTO;
import com.gymmanager.dto.PagedResponseDTO;
import com.gymmanager.dto.ReferralRewardResponseDTO;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.ReferralRewardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final ReferralRewardService referralRewardService;

    /**
     * "Porta un amico" rewards this member has earned. {@code ?status=EARNED} narrows it to
     * the ones still spendable, which is what the subscription form offers.
     */
    @GetMapping("/{id}/rewards")
    public List<ReferralRewardResponseDTO> rewards(
            @PathVariable Long id,
            @RequestParam(required = false) String status
    ) {
        return referralRewardService.findByMember(id, status);
    }

    @GetMapping
    public ResponseEntity<PagedResponseDTO<MemberResponseDTO>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(memberService.findAll(q, active, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.findById(id));
    }

    @PostMapping
    public ResponseEntity<MemberResponseDTO> create(@Valid @RequestBody MemberRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemberResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody MemberRequestDTO dto
    ) {
        return ResponseEntity.ok(memberService.update(id, dto));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<MemberResponseDTO> activate(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.updateStatus(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<MemberResponseDTO> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.updateStatus(id, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
