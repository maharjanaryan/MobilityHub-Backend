// model/Vehicle.java
package com.mobilityhub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // Basic Information
    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Integer year;

    private String color;

    @Column(name = "license_plate", unique = true, nullable = false)
    private String licensePlate;

    @Column(name = "vin", unique = true)
    private String vin;  // Vehicle Identification Number

    // Vehicle Details
    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "transmission")
    private String transmission;

    private Integer seats;
    private Integer doors;
    private Integer luggageCapacity;

    // Features (stored as comma-separated string or JSON)
    @Column(columnDefinition = "TEXT")
    private String features;  // Store as JSON or comma-separated

    // Pricing
    @Column(name = "price_per_day", nullable = false)
    private BigDecimal pricePerDay;

    @Column(name = "price_per_week")
    private BigDecimal pricePerWeek;

    @Column(name = "price_per_month")
    private BigDecimal pricePerMonth;

    @Column(name = "security_deposit")
    private BigDecimal securityDeposit;

    // Location
    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;
    private String state;
    private String zipCode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    // Availability
    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_to")
    private LocalDateTime availableTo;

    @Column(name = "min_rental_days")
    private Integer minRentalDays;

    @Column(name = "max_rental_days")
    private Integer maxRentalDays;

    // Description
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String terms;

    // Photos (stored as JSON array of URLs)
    @Column(columnDefinition = "TEXT")
    private String photos;  // JSON array of image URLs/base64

    // Status
    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    // ✅ ADD THIS FIELD - Rejection reason for admin verification rejection
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // Statistics
    @Column(name = "total_rentals")
    private Integer totalRentals = 0;

    @Column(name = "average_rating")
    private Double averageRating = 0.0;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isAvailable == null) {
            isAvailable = true;
        }
        if (isVerified == null) {
            isVerified = false;
        }
        if (totalRentals == null) {
            totalRentals = 0;
        }
        if (averageRating == null) {
            averageRating = 0.0;
        }
        if (viewCount == null) {
            viewCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}