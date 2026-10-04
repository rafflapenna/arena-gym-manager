package com.gymmanager.service;

import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.PaymentRequestDTO;
import com.gymmanager.dto.PaymentResponseDTO;
import com.gymmanager.entity.Payment;
import com.gymmanager.entity.PaymentMethod;
import com.gymmanager.entity.Subscription;
import com.gymmanager.entity.Member;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PaymentMethodRepository;
import com.gymmanager.repository.PaymentRepository;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CurrentUser currentUser;

    private void assertMemberInScope(Member member) {
        Long gymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    @Transactional
    public PaymentResponseDTO create(Long subscriptionId, PaymentRequestDTO dto) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + subscriptionId));
        assertMemberInScope(subscription.getMember());

        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found with id: " + dto.getPaymentMethodId()));

        Payment payment = Payment.builder()
                .member(subscription.getMember())
                .subscription(subscription)
                .amount(dto.getAmount())
                .paymentDate(dto.getPaymentDate())
                .paymentMethod(method)
                .description(dto.getDescription())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Created payment id={} for subscriptionId={}", saved.getId(), subscriptionId);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> findByMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        assertMemberInScope(member);
        return paymentRepository.findByMemberIdOrderByPaymentDateDesc(memberId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> findBySubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + subscriptionId));
        assertMemberInScope(subscription.getMember());
        return paymentRepository.findBySubscriptionIdOrderByPaymentDateDesc(subscriptionId)
                .stream().map(this::toDTO).toList();
    }

    private PaymentResponseDTO toDTO(Payment p) {
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .memberId(p.getMember().getId())
                .memberFullName(p.getMember().getPersonalInfo().getFirstName() + " " + p.getMember().getPersonalInfo().getLastName())
                .subscriptionId(p.getSubscription().getId())
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .paymentMethod(LookupDTO.builder()
                        .id(p.getPaymentMethod().getId())
                        .code(p.getPaymentMethod().getCode())
                        .label(p.getPaymentMethod().getLabel())
                        .build())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
