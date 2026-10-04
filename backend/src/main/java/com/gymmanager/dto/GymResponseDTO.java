package com.gymmanager.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GymResponseDTO(
        Long id,
        String name,
        String address,
        String city,
        String postalCode,
        String province,
        String region,
        String phone,
        String vatNumber,
        String contactEmail,
        java.math.BigDecimal membershipFeeAmount,
        LocalDateTime createdAt,
        Long instructorId,
        String instructorEmail,
        long staffCount,
        long memberCount
) {}
