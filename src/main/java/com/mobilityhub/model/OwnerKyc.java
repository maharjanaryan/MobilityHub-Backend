// model/OwnerKyc.java - Add this field
package com.mobilityhub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "owner_kyc")
public class OwnerKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Personal Information
    private String fullName;
    private LocalDate dateOfBirth;
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String permanentAddress;

    // Citizenship
    private String citizenshipNumber;
    private String citizenshipFrontImage;
    private String citizenshipBackImage;

    // Driving License
    private String drivingLicenseNumber;
    private LocalDate drivingLicenseExpiryDate;
    private String drivingLicenseImage;

    // Vehicle Ownership Documents (CRITICAL for owners)
    private String vehicleBluebookNumber;
    private String vehicleBluebookImage;
    private String vehicleOwnershipCertificate;

    // Tax & Payment
    private String panNumber;
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolderName;

    // Owner Statistics
    private Integer totalVehiclesListed = 0;
    private Integer totalRentalsProvided = 0;
    private Double ownerRating = 0.0;

    // KYC Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    // ADD THIS MISSING FIELD
    @Builder.Default
    private Boolean ownershipProofVerified = false;  // ← Add this line

    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private String rejectedReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum KycStatus {
        PENDING, SUBMITTED, VERIFIED, REJECTED
    }
}