package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Package reference embedded in {@link SubscriptionResponseDTO} for package-based subscriptions. */
@Data
@Builder
public class SubscriptionPackageRefDTO {
    private Long id;
    private String name;
    private Integer durationMonths;
    private List<NamedRefDTO> courses;
}
