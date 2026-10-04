package com.gymmanager.repository;

import com.gymmanager.entity.FederationCardType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FederationCardTypeRepository extends JpaRepository<FederationCardType, Long> {
    List<FederationCardType> findByFederationIdOrderByPriceAsc(Long federationId);
    boolean existsByFederationIdAndNameIgnoreCase(Long federationId, String name);
}
