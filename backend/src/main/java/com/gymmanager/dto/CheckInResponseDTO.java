package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CheckInResponseDTO {
    private Long id;
    private Long memberId;
    private String memberFullName;
    private LocalDateTime checkInAt;
    private String source;
    /** {@code OK} when there are no warnings, {@code WARNING} otherwise. */
    private String outcome;
    /** Non-blocking eligibility notices (e.g. no active subscription) shown to the operator. */
    private List<String> warnings;
    /** True when the check-in already existed for the day (idempotent re-click; HTTP 200). */
    private boolean alreadyCheckedIn;
}
