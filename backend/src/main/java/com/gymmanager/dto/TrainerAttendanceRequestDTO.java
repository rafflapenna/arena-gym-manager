package com.gymmanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body of {@code POST /trainer/courses/{id}/attendance}. */
@Data
public class TrainerAttendanceRequestDTO {

    @NotNull(message = "memberId is required")
    private Long memberId;
}
