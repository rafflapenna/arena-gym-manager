package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MemberFeeResponseDTO {
    private Long id;
    /** ASSOCIATION | FEDERATION. */
    private String type;
    private NamedRefDTO federation;
    private NamedRefDTO cardType;
    private BigDecimal amount;
    private NamedRefDTO paymentMethod;
    private LocalDate paidOn;
    private LocalDate validUntil;
    /** True when validUntil is in the past — the renewal signal the UI shows. */
    private boolean expired;
    private String notes;
    /** Only populated by the gym-wide "expiring" listing. */
    private NamedRefDTO member;
}
