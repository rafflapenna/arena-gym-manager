package com.gymmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BeltGradeRequestDTO {

    @NotBlank
    private String code;

    @NotBlank
    private String label;
}
