package com.gymmanager.repository;

import com.gymmanager.entity.Discipline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisciplineRepository extends JpaRepository<Discipline, Long> {
    List<Discipline> findByGymIdOrderByNameAsc(Long gymId);
    boolean existsByGymIdAndNameIgnoreCase(Long gymId, String name);
    boolean existsByGymIdAndNameIgnoreCaseAndIdNot(Long gymId, String name, Long id);
}
