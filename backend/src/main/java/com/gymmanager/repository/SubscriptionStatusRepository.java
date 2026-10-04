package com.gymmanager.repository;

import com.gymmanager.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionStatusRepository extends JpaRepository<SubscriptionStatus, Long> {
    Optional<SubscriptionStatus> findByCode(String code);
    boolean existsByCode(String code);
}
