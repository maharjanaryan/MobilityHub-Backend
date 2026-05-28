// model/Vehicle.java
package com.mobilityhub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Vehicle Details
    private String vehicleName;
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;

    // Vehicle Registration
    private String bluebookNumber;
    private LocalDate registrationDate;
    private LocalDate registrationExpiryDate;
    private String pollutionCertificateImage;
    private String insuranceDocument;

    // Rental Pricing
    private BigDecimal dailyRate;
    private BigDecimal weeklyRate;
    private BigDecimal monthlyRate;
    private BigDecimal securityDeposit;

    // Status
    private Boolean isAvailable = true;
    private Boolean isVerified = false;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;

    // Location
    @Column(columnDefinition = "TEXT")
    private String pickupAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;

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
}