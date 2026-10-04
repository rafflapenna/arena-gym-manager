package com.gymmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DisciplineRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String name;

    private Boolean active;

    /** Federazione a cui si tesserano i praticanti; null = nessuna. */
    private Long federationId;
}
