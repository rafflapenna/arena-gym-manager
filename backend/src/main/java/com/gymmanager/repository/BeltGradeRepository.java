package com.gymmanager.repository;

import com.gymmanager.entity.BeltGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeltGradeRepository extends JpaRepository<BeltGrade, Long> {
    Optional<BeltGrade> findByCode(String code);
    boolean existsByCode(String code);
    List<BeltGrade> findAllByOrderByDisplayOrderAsc();
}
