package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A member's card number with a federation (FederKombat "Cod. tessera", …). Unlike a
 * {@link MemberFee}, which is paid every season, the number is the member's identity in that
 * federation and does not change on renewal — so it lives here, once per member and
 * federation, rather than being re-typed on every fee.
 */
@Entity
@Table(name = "member_federation_cards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_federation_cards_member_federation",
                        columnNames = {"member_id", "federation_id"}),
                @UniqueConstraint(name = "uk_member_federation_cards_federation_number",
                        columnNames = {"federation_id", "card_number"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberFederationCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id", nullable = false)
    private Federation federation;

    @Column(name = "card_number", nullable = false, length = 50)
    private String cardNumber;
}
