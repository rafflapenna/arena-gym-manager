package com.gymmanager.entity;

/**
 * Priority bucket for an {@link EmailOutbox} row — drives both the drain order and (once
 * broadcasts exist) which rows get sacrificed first when {@code app.mail.outbox.daily-cap} is
 * tight (shared across every gym on the Brevo free plan).
 */
public enum EmailCategory {
    /** User is waiting on it (password reset, …). Currently sent via {@code sendNow}, not
     *  queued — kept here so a future flow can queue one without a schema change. */
    TRANSACTIONAL,
    /** Service reminders: subscription/fee renewal, medical cert. MVP's only producers. */
    REMINDER,
    /** Opt-in only, not produced yet (roadmap: broadcast, birthdays). */
    MARKETING
}
