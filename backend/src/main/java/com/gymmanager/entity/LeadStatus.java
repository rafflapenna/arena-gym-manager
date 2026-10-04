package com.gymmanager.entity;

import java.util.Set;

/**
 * Lifecycle status of a {@link Lead} (roadmap #8 — free trial / lead tracking), from first
 * contact to becoming a paying {@link Member} or dropping out of the funnel.
 */
public enum LeadStatus {
    /**
     * Submitted by the member themself via {@code POST /me/leads} ("prove inserite dai soci",
     * 2026-10-01); awaiting the reception's confirmation. Not convertible yet — staff must
     * {@code confirm} (-> TRIAL_SCHEDULED) or {@code decline} (-> LOST) it first.
     */
    REQUESTED,
    /** Just created, no trial date set yet. */
    NEW,
    /** Has a trial date in the future. */
    TRIAL_SCHEDULED,
    /** Trial date is today or in the past; ready for follow-up. */
    TRIED,
    /** Became a paying member ({@link Lead#getConvertedMemberId()} is set). */
    CONVERTED,
    /** Did not convert; dropped out of the funnel. */
    LOST;

    /**
     * Counts against a member's cap of open self-reported leads (anti-abuse, "prove inserite
     * dai soci"): not yet tried and not yet resolved one way or another.
     */
    public static final Set<LeadStatus> PENDING = Set.of(REQUESTED, NEW, TRIAL_SCHEDULED);

    /**
     * Still "active" in the funnel for the duplicate-phone check: a phone already attached to
     * one of these blocks a new member-reported lead for the same number, but CONVERTED/LOST
     * ones don't (same person trying again, or a stale attempt, is fine).
     */
    public static final Set<LeadStatus> OPEN = Set.of(REQUESTED, NEW, TRIAL_SCHEDULED, TRIED);
}
