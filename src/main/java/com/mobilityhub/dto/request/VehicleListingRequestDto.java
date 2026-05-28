// dto/request/VehicleListingRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VehicleListingRequestDto {

    @NotBlank(message = "Vehicle name is required")
    private String vehicleName;

    @NotBlank(message = "Brand is required")
    private String brand;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Year is required")
    @Min(value = 1990, message = "Vehicle year must be 1990 or later")
    private Integer year;

    private String color;

    @NotBlank(message = "License plate is required")
    private String licensePlate;

    @NotBlank(message = "Bluebook number is required")
    private String bluebookNumber;

    @NotNull(message = "Registration expiry date is required")
    private LocalDate registrationExpiryDate;

    private String pollutionCertificateImage;
    private String insuranceDocument;

    @NotNull(message = "Daily rate is required")
    @Positive(message = "Daily rate must be positive")
    private BigDecimal dailyRate;

    private BigDecimal weeklyRate;
    private BigDecimal monthlyRate;
    private BigDecimal securityDeposit;

    private String pickupAddress;
}