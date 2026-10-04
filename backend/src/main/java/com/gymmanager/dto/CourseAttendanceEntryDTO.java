package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A single attendance entry as seen from the TRAINER area
 * ({@code /trainer/courses/{id}/attendance}). Field names are part of the FE
 * contract ({@code CourseAttendanceEntry} in the React app).
 */
@Data
@Builder
public class CourseAttendanceEntryDTO {
    private Long checkInId;
    private Long memberId;
    private String memberName;
    private LocalDateTime timestamp;
}
