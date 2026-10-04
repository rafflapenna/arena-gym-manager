package com.gymmanager.security;

/**
 * Role name constants used throughout the application.
 * AppUser.role stores these values as plain strings (no ROLE_ prefix).
 */
public final class Roles {

    public static final String ADMIN       = "ADMIN";
    public static final String GYM_MANAGER = "GYM_MANAGER";
    public static final String STAFF       = "STAFF";
    public static final String TRAINER     = "TRAINER";
    public static final String MEMBER      = "MEMBER";

    private Roles() {}
}
