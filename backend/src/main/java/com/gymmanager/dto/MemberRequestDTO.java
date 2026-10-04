package com.gymmanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MemberRequestDTO {

    @Valid
    @NotNull(message = "Personal info is required")
    private PersonalInfoRequestDTO personalInfo;

    private LocalDate registrationDate;

    private String notes;

    /**
     * Optional: the member who brought this one in. Must be a member of the same gym and not
     * the member itself (validated in MemberService). Drives the referral reward, which is
     * earned when this member takes their first subscription.
     */
    private Long referredByMemberId;
}
