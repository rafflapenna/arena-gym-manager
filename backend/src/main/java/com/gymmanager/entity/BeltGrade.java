package com.gymmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "belt_grades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeltGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int displayOrder;
}
