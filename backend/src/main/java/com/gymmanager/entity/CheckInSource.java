package com.gymmanager.entity;

/**
 * Channel through which a {@link CheckIn} was registered.
 */
public enum CheckInSource {
    /** Registered by an operator at the reception (STAFF/GYM_MANAGER/TRAINER). */
    RECEPTION,
    /** Registered by the member themselves (self check-in). */
    SELF,
    /** Registered by an operator scanning the member's personal QR identity card. */
    QR
}
