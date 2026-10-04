package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gyms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "province", length = 2)
    private String province;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    /**
     * The gym's yearly membership fee ("quota associativa"), changeable at will by the gym
     * manager. A charged fee snapshots the amount onto {@link MemberFee}, so raising this
     * never rewrites what past members were charged.
     */
    @Builder.Default
    @Column(name = "membership_fee_amount", nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal membershipFeeAmount = new java.math.BigDecimal("20.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
