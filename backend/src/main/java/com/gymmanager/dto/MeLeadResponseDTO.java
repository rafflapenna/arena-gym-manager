package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What a member sees of a lead they reported (or that staff logged with them as referrer) in
 * "I miei amici" — deliberately narrower than the staff-side {@link LeadResponseDTO}: no
 * phone/email/notes/source/matchedMemberId/convertedMemberId/referredBy, so a prospect's
 * contact details never round-trip through the socio's own screen.
 */
@Data
@Builder
public class MeLeadResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDate trialDate;
    private NamedRefDTO interestCourse;
    /**
     * Whether bringing this person in has already earned the referral discount (requires the
     * referred person's first subscription, same rule as {@code ReferralRewardService}) —
     * stays true even after the reward is later spent (USED), so the member always sees
     * credit for having brought this friend in.
     */
    private boolean rewardEarned;
    private LocalDateTime createdAt;
}
