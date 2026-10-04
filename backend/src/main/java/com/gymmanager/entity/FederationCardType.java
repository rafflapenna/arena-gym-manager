package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One line of a federation's fee list: a kind of membership card with its price (FederKombat
 * "Agonistica Over 15" 25,00, FIJLKAM "Atleti AG" 30,00, …). A federation has several because
 * the fee depends on age and on competitive level, so a 12-year-old and an adult don't pay the
 * same — the operator picks the line at registration time.
 */
@Entity
@Table(name = "federation_card_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FederationCardType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id", nullable = false)
    private Federation federation;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
