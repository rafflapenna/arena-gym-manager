package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "personal_infos",
        uniqueConstraints = @UniqueConstraint(columnNames = "email", name = "uk_personal_infos_email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 100)
    private String birthPlace;

    @Column(name = "fiscal_code", length = 16)
    private String fiscalCode;

    @Column(length = 1)
    private String gender;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(length = 5)
    private String province;

    @Column(length = 50)
    private String region;

    @Column(length = 100)
    @Builder.Default
    private String country = "Italia";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "belt_grade_id")
    private BeltGrade beltGrade;
}
