package com.gymmanager.repository;

import com.gymmanager.entity.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionTypeRepository extends JpaRepository<SubscriptionType, Long> {
    Optional<SubscriptionType> findByCode(String code);
    boolean existsByCode(String code);

    List<SubscriptionType> findByGymIdOrderByIdAsc(Long gymId);
    boolean existsByGymIdAndCode(Long gymId, String code);

    /** Legacy/global rows that predate the per-gym migration (gym == null). */
    List<SubscriptionType> findByGymIsNullOrderByIdAsc();
}
