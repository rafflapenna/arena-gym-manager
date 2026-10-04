package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A fee a member has paid: either the gym's yearly membership fee or a federation
 * registration. Both are "paid once, valid until a date" — unlike a Subscription, which is
 * what the member is buying, these are what lets them be there at all.
 *
 * <p>The amount is a snapshot, not a lookup: the gym can change its prices whenever it likes
 * and past receipts must not move. {@link #cardType} records which line of the federation's
 * fee list was used, and stays as history even if that line is later renamed or deleted.
 */
@Entity
@Table(name = "member_fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberFeeType type;

    /** Null for the association fee; set for a federation registration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id")
    private Federation federation;

    /** Which fee-list line was charged; null for the association fee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_type_id")
    private FederationCardType cardType;

    /** What was actually charged, snapshotted so later price changes don't rewrite history. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    /** Inclusive: the fee covers up to and including this day. */
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
