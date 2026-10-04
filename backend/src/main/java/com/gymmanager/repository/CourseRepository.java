package com.gymmanager.repository;

import com.gymmanager.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /*
     * Every listing below fetch-joins daysOfWeek + discipline + trainer in one round trip:
     * toDTO reads all three for each row, and with the DB in Frankfurt and the app in Italy
     * an N+1 would cost ~20ms per course (CLAUDE.md §Performance). DISTINCT is required
     * because the daysOfWeek join multiplies each course row by its number of days.
     *
     * Ordering is by start time, then name: a course now runs on several days, so the old
     * "order by day of week" no longer identifies a single position in the week.
     */

    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.daysOfWeek
            LEFT JOIN FETCH c.discipline
            LEFT JOIN FETCH c.trainer
            WHERE c.gym.id = :gymId
            ORDER BY c.startTime ASC, c.name ASC
            """)
    List<Course> findAllForGym(@Param("gymId") Long gymId);

    /**
     * Active courses of a gym running on a given ISO day. {@code MEMBER OF} compiles to an
     * EXISTS subquery, so it filters on one day without narrowing the fetched collection —
     * each returned course still carries all of its days.
     */
    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.daysOfWeek
            LEFT JOIN FETCH c.discipline
            LEFT JOIN FETCH c.trainer
            WHERE c.gym.id = :gymId
              AND c.active = true
              AND :day MEMBER OF c.daysOfWeek
            ORDER BY c.startTime ASC
            """)
    List<Course> findActiveForGymOnDay(@Param("gymId") Long gymId, @Param("day") Integer day);

    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.daysOfWeek
            LEFT JOIN FETCH c.discipline
            LEFT JOIN FETCH c.trainer
            WHERE c.gym.id = :gymId AND c.active = true
            ORDER BY c.name ASC
            """)
    List<Course> findActiveForGym(@Param("gymId") Long gymId);

    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.daysOfWeek
            LEFT JOIN FETCH c.discipline
            LEFT JOIN FETCH c.trainer
            WHERE c.trainer.id = :trainerId
            ORDER BY c.startTime ASC, c.name ASC
            """)
    List<Course> findAllForTrainer(@Param("trainerId") Long trainerId);
}
