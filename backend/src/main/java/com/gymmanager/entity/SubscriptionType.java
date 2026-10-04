package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private Integer months;

    /** List price preset (roadmap: enrollment wizard). Nullable — gyms opt in per type. */
    @Column(name = "default_price", precision = 10, scale = 2)
    private BigDecimal defaultPrice;

    /** Owning gym (tenant). Null only for legacy/global rows that predate the per-gym
     *  migration — kept as historical FK targets for old subscriptions, hidden from
     *  gym-scoped listings and not editable by staff. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id")
    private Gym gym;
}
