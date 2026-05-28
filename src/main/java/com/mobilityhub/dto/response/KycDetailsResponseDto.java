// dto/response/KycDetailsResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class KycDetailsResponseDto {

    // Basic Info
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String kycType;  // RENTER or OWNER
    private String kycStatus;

    // Personal Information
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String phoneNumber;
    private String permanentAddress;
    private String temporaryAddress;

    // Citizenship Documents
    private String citizenshipNumber;
    private String citizenshipFrontImage;
    private String citizenshipBackImage;

    // Driving License
    private String drivingLicenseNumber;
    private LocalDate drivingLicenseIssueDate;
    private LocalDate drivingLicenseExpiryDate;
    private String drivingLicenseImage;

    // Owner Specific Fields (only for OWNER KYC)
    private String vehicleBluebookNumber;
    private String vehicleBluebookImage;
    private String vehicleOwnershipCertificate;
    private Boolean ownershipProofVerified;

    // Payment Information (only for OWNER KYC)
    private String panNumber;
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolderName;

    // Metadata
    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private String verifiedByUsername;
    private String rejectionReason;
    private String adminNotes;
}