package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SortNatural;

import java.time.LocalTime;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A recurring course/class offered by a gym (e.g. "Judo Bambini — Lun/Mer 18:00"). This
 * is the {@code TrainingClass} referenced by {@link CheckIn#getTrainingClassId()}
 * (roadmap §4 A.2): check-ins for a course set {@code trainingClassId = course.getId()}
 * without needing a schema change to CheckIn.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discipline_id", nullable = false)
    private Discipline discipline;

    @Column(nullable = false, length = 100)
    private String name;

    /** Optional level/band label (e.g. "Principianti", "Agonisti"). */
    @Column(length = 50)
    private String level;

    /** The AppUser (role=TRAINER) teaching this course. Nullable: a course can be unassigned.
     *  No DB constraint enforces the TRAINER role (AppUser.role is a free string) — validated
     *  in {@code CourseService} instead, same pattern as other cross-entity business rules. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id")
    private AppUser trainer;

    /**
     * ISO days of the week the course runs on: 1=Monday .. 7=Sunday, at least one and at most
     * seven (enforced in {@code CourseService.resolveDays}). A {@code SortedSet} so the days
     * come out in week order and a day can't be listed twice.
     *
     * <p>Replaces the original single {@code courses.day_of_week} column, which stays in the
     * database — unmapped and nullable — purely as the source of the one-shot backfill in
     * {@code schema.sql}. Hibernate's {@code ddl-auto: update} never drops a column, so it is
     * harmless there; nothing reads it any more.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "course_days", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "day_of_week", nullable = false)
    @SortNatural
    @Builder.Default
    private SortedSet<Integer> daysOfWeek = new TreeSet<>();

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    /** Optional max attendees; null = unlimited. */
    private Integer capacity;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
