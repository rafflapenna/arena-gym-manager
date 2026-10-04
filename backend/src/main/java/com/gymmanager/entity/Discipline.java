package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A discipline offered by a gym (e.g. judo, yoga, MMA). Unlike the global lookups
 * ({@link SubscriptionType}, {@link PaymentMethod}, {@link BeltGrade}) this is
 * per-tenant: name uniqueness is scoped to the owning gym, not the whole table.
 */
@Entity
@Table(name = "disciplines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discipline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The federation members practising this discipline are registered with (MMA ->
     * FederKombat, lotta -> FIJLKAM). Nullable: plenty of disciplines have no federation at
     * all, and mapping it here means the operator configures it once instead of choosing the
     * federation by hand at every registration.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id")
    private Federation federation;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
