package com.gymmanager.repository;

import com.gymmanager.entity.TrainingPackage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<TrainingPackage, Long> {

    /**
     * Single-entity lookup with {@code courses} eagerly joined, so {@code PackageService.toDTO}
     * (called from {@code findById}, {@code update}, {@code delete}) never triggers a separate
     * lazy-load round trip for the {@code @ManyToMany} collection.
     */
    @Override
    @EntityGraph(attributePaths = {"courses"})
    Optional<TrainingPackage> findById(Long id);

    // DISTINCT: the LEFT JOIN FETCH on the courses collection (@ManyToMany) would otherwise
    // return one row per (package, course) pair, duplicating packages with more than one course.
    @Query("""
            SELECT DISTINCT p FROM TrainingPackage p LEFT JOIN FETCH p.courses
            WHERE p.gym.id = :gymId
            ORDER BY p.name ASC
            """)
    List<TrainingPackage> findByGymIdOrderByNameAsc(@Param("gymId") Long gymId);

    @Query("""
            SELECT DISTINCT p FROM TrainingPackage p LEFT JOIN FETCH p.courses
            WHERE p.gym.id = :gymId AND p.active = true
            ORDER BY p.name ASC
            """)
    List<TrainingPackage> findByGymIdAndActiveTrueOrderByNameAsc(@Param("gymId") Long gymId);
}
