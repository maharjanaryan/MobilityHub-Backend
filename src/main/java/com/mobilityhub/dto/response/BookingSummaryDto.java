// BookingSummaryDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingSummaryDto {
    private Long bookingId;
    private String bookingReference;
    private String renterName;
    private String renterEmail;
    private String renterPhone;
    private LocalDateTime pickupDate;
    private LocalDateTime dropoffDate;
    private String status;
    private BigDecimal totalAmount;
    private Integer totalDays;
    private String paymentStatus;
    private String paymentMethod;
}