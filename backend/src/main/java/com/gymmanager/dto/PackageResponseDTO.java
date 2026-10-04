package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PackageResponseDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer durationMonths;
    private boolean active;
    private List<NamedRefDTO> courses;
}
