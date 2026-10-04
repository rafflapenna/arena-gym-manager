package com.gymmanager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubscriptionTypeRequestDTO {

    @NotBlank
    private String code;

    @NotBlank
    private String label;

    @NotNull
    @Min(1)
    private Integer months;

    /** Optional list price preset. Null keeps the type price-less (form stays manual, as today). */
    @DecimalMin("0.01")
    private BigDecimal defaultPrice;
}
