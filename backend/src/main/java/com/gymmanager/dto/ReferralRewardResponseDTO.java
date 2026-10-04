package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ReferralRewardResponseDTO {
    private Long id;
    /** {id, name} of the member whose sign-up earned this reward. */
    private NamedRefDTO referredMember;
    private BigDecimal percent;
    /** EARNED | USED | EXPIRED. */
    private String status;
    private LocalDate expiresAt;
    /** Id of the subscription the discount was applied to; null while still spendable. */
    private Long usedOnSubscriptionId;
    private LocalDateTime earnedAt;
    private LocalDateTime usedAt;
}
