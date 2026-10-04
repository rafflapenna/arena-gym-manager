package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DisciplineResponseDTO {
    private Long id;
    private String name;
    private boolean active;
    private NamedRefDTO federation;
}
