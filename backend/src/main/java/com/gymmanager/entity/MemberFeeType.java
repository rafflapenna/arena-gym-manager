package com.gymmanager.entity;

/** What a {@link MemberFee} is for. */
public enum MemberFeeType {
    /** The gym's own yearly membership fee ("quota associativa"), 12 months from payment. */
    ASSOCIATION,
    /** Registration with a sports federation, valid for that federation's season. */
    FEDERATION
}
