package com.gymmanager.repository;

import com.gymmanager.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Bulk-remove all payments of a member (used when deleting the member). */
    @Modifying
    @Query("DELETE FROM Payment p WHERE p.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.member m JOIN FETCH m.personalInfo
            JOIN FETCH p.subscription JOIN FETCH p.paymentMethod
            WHERE p.member.id = :memberId
            ORDER BY p.paymentDate DESC
            """)
    List<Payment> findByMemberIdOrderByPaymentDateDesc(@Param("memberId") Long memberId);

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.member m JOIN FETCH m.personalInfo
            JOIN FETCH p.subscription JOIN FETCH p.paymentMethod
            WHERE p.subscription.id = :subscriptionId
            ORDER BY p.paymentDate DESC
            """)
    List<Payment> findBySubscriptionIdOrderByPaymentDateDesc(@Param("subscriptionId") Long subscriptionId);

    /**
     * Sum of payment amounts for a gym within a date range (inclusive), used by the
     * dashboard "monthly revenue" card. Aggregated in the DB so no entities are
     * loaded into memory; {@code COALESCE} avoids a null result when there are no
     * matching payments.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.member.gym.id = :gymId
              AND p.paymentDate >= :from
              AND p.paymentDate <= :to
            """)
    BigDecimal sumAmountByGymIdAndPaymentDateBetween(
            @Param("gymId") Long gymId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
