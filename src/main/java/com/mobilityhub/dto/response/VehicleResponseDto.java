// dto/response/VehicleResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VehicleResponseDto {
    private Long id;
    private Long ownerId;
    private String ownerName;
    private String ownerAvatar;
    private Double ownerRating;
    private Integer ownerTotalVehicles;

    // Basic Information
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;

    // Vehicle Details
    private String fuelType;
    private String transmission;
    private Integer seats;
    private Integer doors;
    private Integer luggageCapacity;
    private List<String> features;

    // Pricing
    private BigDecimal pricePerDay;
    private BigDecimal pricePerWeek;
    private BigDecimal pricePerMonth;
    private BigDecimal securityDeposit;

    // Location
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    // Availability
    private Boolean isAvailable;
    private LocalDateTime availableFrom;
    private LocalDateTime availableTo;
    private Integer minRentalDays;
    private Integer maxRentalDays;

    // Description
    private String description;
    private String terms;

    // Photos
    private List<String> photos;

    // Statistics
    private Integer totalRentals;
    private Double averageRating;
    private Integer viewCount;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
