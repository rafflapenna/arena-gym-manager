package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LookupDTO {
    private Long id;
    private String code;
    private String label;
    private Integer months; // only for SubscriptionType, null otherwise
    private BigDecimal defaultPrice; // only for SubscriptionType, null otherwise
}
