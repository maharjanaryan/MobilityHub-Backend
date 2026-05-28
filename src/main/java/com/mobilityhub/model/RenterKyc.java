// model/RenterKyc.java
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
@Table(name = "renter_kyc")
public class RenterKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Personal Information
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(columnDefinition = "TEXT")
    private String temporaryAddress;

    // Citizenship
    private String citizenshipNumber;
    private String citizenshipFrontImage;
    private String citizenshipBackImage;

    // Driving License (Required for renting)
    private String drivingLicenseNumber;
    private LocalDate drivingLicenseIssueDate;
    private LocalDate drivingLicenseExpiryDate;
    private String drivingLicenseImage;

    // Renter Statistics
    private Boolean hasPreviousRentals = false;
    private Integer totalRentalsCompleted = 0;
    private Double renterRating = 0.0;

    // KYC Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

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
        PENDING,     // No KYC submitted
        SUBMITTED,   // Documents submitted, under review
        VERIFIED,    // Can rent vehicles
        REJECTED
    }
}