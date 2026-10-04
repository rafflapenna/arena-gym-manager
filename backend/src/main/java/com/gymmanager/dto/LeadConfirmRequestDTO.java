package com.gymmanager.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Body of {@code POST /leads/{id}/confirm}: the reception picks/confirms the trial date. */
@Data
public class LeadConfirmRequestDTO {

    @NotNull(message = "Trial date is required")
    @FutureOrPresent(message = "Trial date must be today or in the future")
    private LocalDate trialDate;
}
