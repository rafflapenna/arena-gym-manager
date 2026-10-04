package com.gymmanager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GymRequestDTO(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 10) String postalCode,
        @Size(max = 2) String province,
        @Size(max = 50) String region,
        @Size(max = 30) String phone,
        @Size(max = 20) String vatNumber,
        @Email @Size(max = 150) String contactEmail,
        /** Quota associativa annuale. Null in update = lascia invariato. */
        @DecimalMin("0.00") java.math.BigDecimal membershipFeeAmount
) {}
