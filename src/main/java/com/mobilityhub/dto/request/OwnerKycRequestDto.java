// dto/request/OwnerKycRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class OwnerKycRequestDto {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Invalid phone number")
    private String phoneNumber;

    @NotBlank(message = "Permanent address is required")
    private String permanentAddress;

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

    @NotNull(message = "Driving license expiry date is required")
    private LocalDate drivingLicenseExpiryDate;

    @NotBlank(message = "Driving license image is required")
    private String drivingLicenseImage;

    // Vehicle Ownership Documents (MANDATORY for sharing vehicle)
    @NotBlank(message = "Vehicle Bluebook number is required")
    private String vehicleBluebookNumber;

    @NotBlank(message = "Vehicle Bluebook image is required")
    private String vehicleBluebookImage;

    // Optional: Vehicle ownership certificate (if different from bluebook)
    private String vehicleOwnershipCertificate;  // ← Add this field if you need it

    // Payment Information
    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    private String bankAccountHolderName;
    private String panNumber;
}