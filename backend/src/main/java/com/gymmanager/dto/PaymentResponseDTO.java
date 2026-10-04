package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponseDTO {
    private Long id;
    private Long memberId;
    private String memberFullName;
    private Long subscriptionId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private LookupDTO paymentMethod;
    private String description;
    private LocalDateTime createdAt;
}
