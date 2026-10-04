package com.gymmanager.service;

/**
 * Outcome of one SMTP send attempt — the hand-off between {@link EmailOutboxDrainJob#drain}
 * (which sends, outside any transaction) and {@link EmailOutboxBatchService#applyOutcomes}
 * (which persists the result, in its own transaction).
 */
record EmailSendOutcome(Kind kind, String error) {

    enum Kind { SENT, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    static EmailSendOutcome sent() {
        return new EmailSendOutcome(Kind.SENT, null);
    }

    static EmailSendOutcome transientFailure(String error) {
        return new EmailSendOutcome(Kind.TRANSIENT_FAILURE, error);
    }

    static EmailSendOutcome permanentFailure(String error) {
        return new EmailSendOutcome(Kind.PERMANENT_FAILURE, error);
    }
}
