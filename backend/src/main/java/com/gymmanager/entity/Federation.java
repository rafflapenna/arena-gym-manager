package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A sports federation a gym registers its members with (FederKombat for MMA, FIJLKAM for
 * lotta, …). Per-gym like {@link Discipline} and {@link SubscriptionType}, not a global
 * lookup: each gym keeps and edits its own fee list, seeded from the national defaults by
 * {@code FederationSeeder}. Nothing about the specific federations is hardcoded — a gym that
 * does judo or karate adds its own.
 */
@Entity
@Table(name = "federations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Federation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Month (1-12) the registration season starts on, running 12 months from the 1st of it.
     * The two federations differ and both must be right: FederKombat runs 1 September →
     * 31 August (month 9), FIJLKAM expires 31 December, i.e. the solar year (month 1).
     */
    @Column(name = "season_start_month", nullable = false)
    private int seasonStartMonth;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * First and last day of the season containing {@code date}. With a start month of 9, a
     * payment in October 2026 belongs to 1 Sep 2026 → 31 Aug 2027, while one in March 2026
     * belongs to the season that began in September 2025 — the member is paying into the
     * season already running, not starting a new one.
     */
    public LocalDate seasonStart(LocalDate date) {
        int year = date.getMonthValue() >= seasonStartMonth ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, seasonStartMonth, 1);
    }

    public LocalDate seasonEnd(LocalDate date) {
        return seasonStart(date).plusYears(1).minusDays(1);
    }
}
