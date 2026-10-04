package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberDocumentResponseDTO {
    private Long id;
    private Long memberId;
    private String type;
    private String title;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private LocalDate expiryDate;
    private LocalDateTime uploadedAt;
}
