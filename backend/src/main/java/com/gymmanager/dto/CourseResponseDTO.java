package com.gymmanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class CourseResponseDTO {
    private Long id;
    private String name;
    private String level;
    private NamedRefDTO discipline;
    private TrainerRefDTO trainer;
    /** ISO days the course runs on, ascending (1=Monday .. 7=Sunday). */
    private List<Integer> daysOfWeek;

    /**
     * Legacy mirror of the first day in {@link #daysOfWeek}, for clients not yet updated to
     * the multi-day field. Remove once no deployed frontend reads it.
     */
    private Integer dayOfWeek;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    private Integer durationMin;
    private Integer capacity;
    private boolean active;
}
