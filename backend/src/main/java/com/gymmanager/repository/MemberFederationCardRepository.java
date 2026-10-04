package com.gymmanager.repository;

import com.gymmanager.entity.MemberFederationCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberFederationCardRepository extends JpaRepository<MemberFederationCard, Long> {

    @EntityGraph(attributePaths = {"federation"})
    List<MemberFederationCard> findByMemberIdOrderByFederationNameAsc(Long memberId);

    Optional<MemberFederationCard> findByMemberIdAndFederationId(Long memberId, Long federationId);

    /** The number is the member's identity in the federation: two members can't share it. */
    boolean existsByFederationIdAndCardNumberAndMemberIdNot(Long federationId, String cardNumber, Long memberId);

    /** Guards deleting a federation that members still carry a card number of. */
    boolean existsByFederationId(Long federationId);

    @Modifying
    @Query("DELETE FROM MemberFederationCard c WHERE c.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
