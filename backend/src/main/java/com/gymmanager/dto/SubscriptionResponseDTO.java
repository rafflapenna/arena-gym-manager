package com.gymmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionResponseDTO {
    private Long id;
    private Long memberId;
    private String memberFullName;
    /** Null for package-based subscriptions (roadmap §4 A.4) — check {@code trainingPackage} instead. */
    private LookupDTO type;
    /** Null for classic type-based subscriptions. Serialized as {@code package} (the FE
     *  contract name — not usable as a Java field name since it's a reserved word). */
    @JsonProperty("package")
    private SubscriptionPackageRefDTO trainingPackage;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal price;
    /** Snapshot of the package's price at purchase time; null for type-based subscriptions. */
    private BigDecimal purchasePrice;
    private LookupDTO status;
    private String notes;
    private LocalDateTime createdAt;
}
