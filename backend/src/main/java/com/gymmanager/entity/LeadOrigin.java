package com.gymmanager.entity;

/**
 * Who created a {@link Lead}: the reception desk (the only path before this feature), or the
 * member themself via {@code POST /me/leads} ("prove inserite dai soci", 2026-10-01). A
 * MEMBER-origin lead always starts {@code REQUESTED} and needs staff confirmation before it is
 * convertible.
 */
public enum LeadOrigin {
    STAFF,
    MEMBER
}
