package com.gymmanager.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonalInfoRequestDTO {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String email;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @Size(max = 100)
    private String birthPlace;

    @Size(max = 16)
    private String fiscalCode;

    @Size(max = 1)
    private String gender;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 10)
    private String postalCode;

    @Size(max = 5)
    private String province;

    @Size(max = 50)
    private String region;

    @Size(max = 100)
    private String country;

    private Long beltGradeId;
}
