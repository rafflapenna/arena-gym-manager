package com.gymmanager.entity;

/**
 * Category of a {@link MemberDocument}.
 */
public enum DocumentType {
    /** The only enriched category: carries an {@code expiryDate} and feeds the reminder job. */
    MEDICAL_CERT,
    /** Generic, unlimited, free-title attachment. */
    OTHER
}
