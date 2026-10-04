package com.gymmanager.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FederationRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String name;

    /** Month the registration season starts on: 9 = 1 September, 1 = solar year. */
    @NotNull
    @Min(1)
    @Max(12)
    private Integer seasonStartMonth;

    private Boolean active;
}
