package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class FederationResponseDTO {
    private Long id;
    private String name;
    private Integer seasonStartMonth;
    private boolean active;
    /** Season the fee would cover if paid today — so the UI can show it without recomputing. */
    private LocalDate currentSeasonStart;
    private LocalDate currentSeasonEnd;
    private List<FederationCardTypeResponseDTO> cardTypes;
}
