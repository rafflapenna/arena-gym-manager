package com.gymmanager.controller;

import com.gymmanager.dto.ChangePasswordRequestDTO;
import com.gymmanager.dto.CheckInResponseDTO;
import com.gymmanager.dto.CourseResponseDTO;
import com.gymmanager.dto.MemberDocumentResponseDTO;
import com.gymmanager.dto.MemberFederationCardResponseDTO;
import com.gymmanager.dto.MemberResponseDTO;
import com.gymmanager.dto.PaymentResponseDTO;
import com.gymmanager.dto.PersonalInfoResponseDTO;
import com.gymmanager.dto.QrTokenResponseDTO;
import com.gymmanager.dto.SubscriptionResponseDTO;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.service.CheckInService;
import com.gymmanager.service.CheckinTokenService;
import com.gymmanager.service.CourseService;
import com.gymmanager.service.MemberAccountService;
import com.gymmanager.service.MemberDocumentService;
import com.gymmanager.service.MemberFeeService;
import com.gymmanager.service.MemberService;
import com.gymmanager.service.PaymentService;
import com.gymmanager.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;
    private final MemberAccountService memberAccountService;
    private final CheckInService checkInService;
    private final CourseService courseService;
    private final CheckinTokenService checkinTokenService;
    private final MemberDocumentService memberDocumentService;
    private final MemberFeeService memberFeeService;

    /**
     * The member's own profile, deliberately reduced to what the member area shows: name,
     * email (their login) and belt. Fiscal code, phone, birth data, address and the staff's
     * internal notes stay out of the response, not just out of the page — anything sent here
     * is readable from the browser's network tab or by whoever picks up an unlocked phone.
     */
    @GetMapping
    public MemberResponseDTO getMe() {
        MemberResponseDTO full = memberService.findById(getCurrentMemberId());
        PersonalInfoResponseDTO pi = full.getPersonalInfo();
        return MemberResponseDTO.builder()
                .id(full.getId())
                .personalInfo(PersonalInfoResponseDTO.builder()
                        .firstName(pi.getFirstName())
                        .lastName(pi.getLastName())
                        .email(pi.getEmail())
                        .beltGrade(pi.getBeltGrade())
                        .build())
                .registrationDate(full.getRegistrationDate())
                .active(full.getActive())
                .build();
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionResponseDTO> getMySubscriptions() {
        return subscriptionService.findByMember(getCurrentMemberId());
    }

    @GetMapping("/payments")
    public List<PaymentResponseDTO> getMyPayments() {
        return paymentService.findByMember(getCurrentMemberId());
    }

    @PostMapping("/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        memberAccountService.changePassword(getCurrentMemberId(), dto);
    }

    /**
     * Self check-in. Registers the member's attendance for the day. Rejected with 409
     * when the member is not eligible (no active subscription) — they must use the reception.
     * Idempotent: a second call the same day returns the existing entry (HTTP 200).
     */
    @PostMapping("/check-ins")
    public ResponseEntity<CheckInResponseDTO> checkInSelf() {
        CheckInResponseDTO dto = checkInService.checkIn(getCurrentMemberId(), CheckInSource.SELF);
        HttpStatus status = dto.isAlreadyCheckedIn() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(dto);
    }

    @GetMapping("/check-ins")
    public List<CheckInResponseDTO> getMyCheckIns() {
        return checkInService.findByMember(getCurrentMemberId());
    }

    /** Active course catalog of the member's gym, read-only. */
    @GetMapping("/courses")
    public List<CourseResponseDTO> getMyCourses() {
        return courseService.findActiveForGym(getPrincipal().gymId());
    }

    /** The member's own QR identity card token (roadmap #5), generated lazily on first read. */
    @GetMapping("/qr")
    public QrTokenResponseDTO getMyQrToken() {
        return new QrTokenResponseDTO(checkinTokenService.getTokenForCurrentMember());
    }

    /** The member's own documents. memberId always comes from the principal, never the path. */
    @GetMapping("/documents")
    public List<MemberDocumentResponseDTO> getMyDocuments() {
        return memberDocumentService.findByMember(getCurrentMemberId());
    }

    @GetMapping("/documents/{docId}/download")
    public ResponseEntity<ByteArrayResource> downloadMyDocument(@PathVariable Long docId) {
        return MemberDocumentController.downloadResponse(
                memberDocumentService.download(getCurrentMemberId(), docId));
    }

    /** The member's own federation card numbers (e.g. FederKombat). */
    @GetMapping("/federation-cards")
    public List<MemberFederationCardResponseDTO> getMyFederationCards() {
        return memberFeeService.findCards(getCurrentMemberId());
    }

    private Long getCurrentMemberId() {
        return getPrincipal().memberId();
    }

    private com.gymmanager.security.AuthPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (com.gymmanager.security.AuthPrincipal) authentication.getDetails();
    }
}
