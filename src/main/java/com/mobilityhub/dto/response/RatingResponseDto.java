package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponseDto {
    private Long id;
    private Long bookingId;
    private Long vehicleId;
    private String vehicleName;
    private String vehicleBrand;
    private String vehicleModel;
    private Long renterId;
    private String renterName;
    private Integer rating;
    private String review;
    private LocalDateTime createdAt;
}