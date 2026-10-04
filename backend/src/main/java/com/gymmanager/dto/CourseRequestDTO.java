package com.gymmanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class CourseRequestDTO {

    @NotBlank
    private String name;

    @NotNull
    private Long disciplineId;

    private String level;

    /** Optional: must reference an AppUser with role=TRAINER in the same gym (validated in service). */
    private Long trainerId;

    /**
     * ISO days the course runs on: 1=Monday .. 7=Sunday, at least one. Duplicates are folded
     * and the set is sorted by {@code CourseService.resolveDays}, which also raises the 422
     * when neither this nor the legacy {@link #dayOfWeek} is provided — it can't be
     * {@code @NotEmpty} here or a client still sending only the old single field would fail.
     */
    @Size(max = 7, message = "A course cannot run on more than 7 days")
    private List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek;

    /**
     * Legacy single-day field, superseded by {@link #daysOfWeek}. Kept accepted (and still
     * emitted in the response) so a client that has not been updated yet keeps working during
     * the window where the backend is deployed before the frontend. Ignored when
     * {@code daysOfWeek} is present.
     */
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull
    @Positive
    private Integer durationMin;

    @Positive
    private Integer capacity;

    private Boolean active;
}
