// com/mobilityhub/dto/response/BookingResponseDto.java
package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    private Long id;
    private String bookingReference;

    // Vehicle info
    private Long vehicleId;
    private String vehicleName;
    private String vehicleImage;
    private String vehicleBrand;
    private String vehicleModel;
    private List<String> vehicleBluebookDocuments; // ADDED - Bluebook documents

    // Owner info
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    // Renter info
    private Long renterId;
    private String renterName;
    private String renterEmail;
    private String renterPhone;

    // Renter Location (ONLY LOCATION FIELD)
    private String renterLocation;
    private Double renterLatitude;
    private Double renterLongitude;

    // Dates
    private LocalDateTime pickupDate;
    private LocalDateTime dropoffDate;
    private Integer totalDays;

    // Pricing
    private BigDecimal dailyRate;
    private BigDecimal totalAmount;
    private BigDecimal securityDeposit;

    // Insurance
    private String insuranceType;
    private BigDecimal insuranceCost;

    // Status
    private String bookingStatus;
    private String paymentStatus;
    private String paymentMethod;

    // Notes
    private String rejectionReason;
    private String ownerNotes;

    // Trip
    private LocalDateTime tripStartedAt;
    private LocalDateTime tripEndedAt;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime updatedAt;
}