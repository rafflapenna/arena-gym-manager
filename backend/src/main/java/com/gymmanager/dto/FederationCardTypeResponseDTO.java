package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FederationCardTypeResponseDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private boolean active;
}
