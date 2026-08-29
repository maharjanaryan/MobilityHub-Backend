package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRatingSummaryDto {
    private Long vehicleId;
    private String vehicleName;
    private String vehicleBrand;
    private String vehicleModel;
    private Double averageRating;
    private Integer totalRatings;
    private Integer rating5Count;
    private Integer rating4Count;
    private Integer rating3Count;
    private Integer rating2Count;
    private Integer rating1Count;
    private Map<Integer, Integer> ratingDistribution;
    private List<RatingResponseDto> recentReviews;
}