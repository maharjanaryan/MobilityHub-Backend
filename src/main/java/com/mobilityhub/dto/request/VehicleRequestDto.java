// dto/request/VehicleRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VehicleRequestDto {

    // Basic Information
    @NotBlank(message = "Brand is required")
    private String brand;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Year is required")
    @Min(value = 1990, message = "Year must be 1990 or later")
    @Max(value = 2026, message = "Year cannot be in the future")
    private Integer year;

    private String color;

    @NotBlank(message = "License plate is required")
    private String licensePlate;

    private String vin;

    // Vehicle Details
    private String fuelType;
    private String transmission;
    private Integer seats;
    private Integer doors;
    private Integer luggageCapacity;
    private List<String> features;

    // Pricing
    @NotNull(message = "Price per day is required")
    @Positive(message = "Price must be positive")
    private BigDecimal pricePerDay;

    private BigDecimal pricePerWeek;
    private BigDecimal pricePerMonth;
    private BigDecimal securityDeposit;

    // Location
    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private String state;

    @NotBlank(message = "ZIP code is required")
    private String zipCode;

    private BigDecimal latitude;
    private BigDecimal longitude;

    // Availability
    private LocalDateTime availableFrom;
    private LocalDateTime availableTo;
    private Integer minRentalDays;
    private Integer maxRentalDays;

    // Description
    @NotBlank(message = "Description is required")
    private String description;

    private String terms;

    // Photos
    private List<String> photos;
}
