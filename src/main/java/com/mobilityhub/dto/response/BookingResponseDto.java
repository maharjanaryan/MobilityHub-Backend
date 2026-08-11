package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingResponseDto {
    private Long id;
    private String bookingReference;
    private Long vehicleId;
    private String vehicleName;
    private String vehicleImage;
    private Long ownerId;
    private String ownerName;
    private Long renterId;
    private String renterName;
    private String renterEmail;
    private String renterPhone;
    private LocalDateTime pickupDate;
    private LocalDateTime dropoffDate;
    private Integer totalDays;
    private BigDecimal dailyRate;
    private BigDecimal totalAmount;
    private BigDecimal securityDeposit;
    private String insuranceType;
    private BigDecimal insuranceCost;
    private String bookingStatus;
    private String paymentStatus;
    private String paymentMethod;  // ← ADD THIS FIELD
    private String rejectionReason;
    private String ownerNotes;     // ← ADD THIS FIELD (optional but recommended)
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}