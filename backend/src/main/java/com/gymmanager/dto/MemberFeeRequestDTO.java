package com.gymmanager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Charging a fee. {@code type} decides which of the other fields matter:
 * ASSOCIATION ignores the federation fields, FEDERATION requires {@code cardTypeId}.
 */
@Data
public class MemberFeeRequestDTO {

    @NotNull
    private String type;

    /** Required for a FEDERATION fee; the federation and the default amount follow from it. */
    private Long cardTypeId;

    /** Optional override: leave null to charge the configured price. */
    @DecimalMin("0.00")
    private BigDecimal amount;

    private Long paymentMethodId;

    /** Defaults to today; the validity window is computed from it. */
    private LocalDate paidOn;

    private String notes;
}
