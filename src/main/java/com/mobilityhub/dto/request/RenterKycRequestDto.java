// dto/request/RenterKycRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class RenterKycRequestDto {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    private String gender;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Invalid phone number")
    private String phoneNumber;

    @NotBlank(message = "Permanent address is required")
    private String permanentAddress;

    private String temporaryAddress;

    // Citizenship
    @NotBlank(message = "Citizenship number is required")
    private String citizenshipNumber;

    @NotBlank(message = "Citizenship front image is required")
    private String citizenshipFrontImage;

    @NotBlank(message = "Citizenship back image is required")
    private String citizenshipBackImage;

    // Driving License
    @NotBlank(message = "Driving license number is required")
    private String drivingLicenseNumber;

    @NotNull(message = "Driving license issue date is required")
    private LocalDate drivingLicenseIssueDate;

    @NotNull(message = "Driving license expiry date is required")
    private LocalDate drivingLicenseExpiryDate;

    @NotBlank(message = "Driving license image is required")
    private String drivingLicenseImage;
}