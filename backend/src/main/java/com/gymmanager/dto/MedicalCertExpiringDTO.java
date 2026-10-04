package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class MedicalCertExpiringDTO {
    private Long documentId;
    private Long memberId;
    private String memberFullName;
    private LocalDate expiryDate;
    private long daysUntilExpiry;
}
