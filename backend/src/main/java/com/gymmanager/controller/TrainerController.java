package com.gymmanager.controller;

import com.gymmanager.dto.CheckInResponseDTO;
import com.gymmanager.dto.CourseAttendanceEntryDTO;
import com.gymmanager.dto.CourseResponseDTO;
import com.gymmanager.dto.TrainerAttendanceRequestDTO;
import com.gymmanager.entity.CheckInSource;
import com.gymmanager.service.CheckInService;
import com.gymmanager.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * TRAINER-only area (route access enforced by the {@code /api/v1/trainer/**} matcher
 * in SecurityConfig). A trainer only sees their own courses; every course id is
 * resolved through {@link CourseService#getCourseOwnedByCurrentTrainer} so foreign
 * courses look like 404 (anti-IDOR, roadmap §4 §C).
 *
 * <p>Attendance is scoped per course: marking a member present here tags the
 * {@code CheckIn} with this course's id ({@code trainingClassId}), and the attendance
 * list only returns check-ins tagged with that same course. Reception/self check-ins
 * (which have no course context) are never attributed to a course and won't appear here.
 */
@RestController
@RequestMapping("/api/v1/trainer")
@RequiredArgsConstructor
public class TrainerController {

    private final CourseService courseService;
    private final CheckInService checkInService;

    @GetMapping("/courses")
    public List<CourseResponseDTO> getMyCourses() {
        return courseService.findMyCourses();
    }

    @GetMapping("/courses/{id}/attendance")
    public List<CourseAttendanceEntryDTO> getAttendance(@PathVariable Long id) {
        courseService.getCourseOwnedByCurrentTrainer(id);
        return checkInService.findByCourseOnDate(id, LocalDate.now())
                .stream().map(this::toEntry).toList();
    }

    @PostMapping("/courses/{id}/attendance")
    public ResponseEntity<CourseAttendanceEntryDTO> markAttendance(
            @PathVariable Long id,
            @Valid @RequestBody TrainerAttendanceRequestDTO dto
    ) {
        courseService.getCourseOwnedByCurrentTrainer(id);
        // A member of another gym fails the scope check inside checkIn() with 404.
        CheckInResponseDTO checkIn = checkInService.checkIn(dto.getMemberId(), CheckInSource.RECEPTION, id);
        HttpStatus status = checkIn.isAlreadyCheckedIn() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(toEntry(checkIn));
    }

    private CourseAttendanceEntryDTO toEntry(CheckInResponseDTO c) {
        return CourseAttendanceEntryDTO.builder()
                .checkInId(c.getId())
                .memberId(c.getMemberId())
                .memberName(c.getMemberFullName())
                .timestamp(c.getCheckInAt())
                .build();
    }
}
