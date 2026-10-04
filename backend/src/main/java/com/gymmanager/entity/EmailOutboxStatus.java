package com.gymmanager.entity;

/** Lifecycle of an {@link EmailOutbox} row, driven by {@code EmailOutboxDrainJob}. */
public enum EmailOutboxStatus {
    /** Queued, waiting for the drainer (or for {@code scheduledAt}/{@code nextAttemptAt}). */
    PENDING,
    /** Claimed by a drain cycle; the SMTP send is in flight (or the process crashed mid-send —
     *  see the stale-SENDING recovery). */
    SENDING,
    /** Delivered to the SMTP server. */
    SENT,
    /** Gave up: either a permanent error (invalid address) or {@code max-attempts} transient
     *  failures. */
    FAILED,
    /** Reserved for the scheduled-broadcast case (roadmap phase 2) — not set by anything yet. */
    CANCELLED
}
