package com.gymmanager.repository;

import com.gymmanager.entity.Federation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FederationRepository extends JpaRepository<Federation, Long> {
    List<Federation> findByGymIdOrderByNameAsc(Long gymId);
    Optional<Federation> findByGymIdAndNameIgnoreCase(Long gymId, String name);
    boolean existsByGymIdAndNameIgnoreCase(Long gymId, String name);
    boolean existsByGymIdAndNameIgnoreCaseAndIdNot(Long gymId, String name, Long id);
}
