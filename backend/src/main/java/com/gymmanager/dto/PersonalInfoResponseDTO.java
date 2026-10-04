package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PersonalInfoResponseDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private String birthPlace;
    private String fiscalCode;
    private String gender;
    private String address;
    private String city;
    private String postalCode;
    private String province;
    private String region;
    private String country;
    private LookupDTO beltGrade;
}
