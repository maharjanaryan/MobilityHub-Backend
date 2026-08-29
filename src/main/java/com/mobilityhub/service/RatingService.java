package com.mobilityhub.service;

import com.mobilityhub.dto.request.RatingRequestDto;
import com.mobilityhub.dto.response.RatingResponseDto;
import com.mobilityhub.dto.response.VehicleRatingSummaryDto;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.Rating;
import com.mobilityhub.model.Vehicle;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.RatingRepository;
import com.mobilityhub.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional
    public RatingResponseDto submitRating(Long userId, RatingRequestDto request) {
        // Validate booking exists
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Check if user is the renter
        if (!booking.getRenter().getId().equals(userId)) {
            throw new RuntimeException("Only the renter can rate this booking");
        }

        // Check if booking is completed
        if (booking.getBookingStatus() != Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Can only rate completed bookings");
        }

        // Check if rating already exists
        if (ratingRepository.existsByBookingId(request.getBookingId())) {
            throw new RuntimeException("This booking has already been rated");
        }

        // Create rating
        Rating rating = Rating.builder()
                .booking(booking)
                .vehicle(booking.getVehicle())
                .renter(booking.getRenter())
                .rating(request.getRating())
                .review(request.getReview())
                .build();

        Rating savedRating = ratingRepository.save(rating);

        // Update vehicle average rating
        updateVehicleAverageRating(booking.getVehicle().getId());

        log.info("Rating submitted for booking {} by user {}: {} stars",
                booking.getId(), userId, request.getRating());

        return convertToResponseDto(savedRating);
    }

    @Transactional
    public void updateVehicleAverageRating(Long vehicleId) {
        Double avgRating = ratingRepository.getAverageRatingForVehicle(vehicleId);
        Integer totalRatings = ratingRepository.getTotalRatingsForVehicle(vehicleId);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        vehicle.setAverageRating(avgRating != null ? avgRating : 0.0);
        vehicle.setTotalRatings(totalRatings != null ? totalRatings : 0);
        vehicleRepository.save(vehicle);

        log.info("Updated vehicle {} average rating: {}, total ratings: {}",
                vehicleId, avgRating, totalRatings);
    }

    public RatingResponseDto getRatingByBookingId(Long bookingId) {
        Rating rating = ratingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Rating not found for this booking"));
        return convertToResponseDto(rating);
    }

    public Page<RatingResponseDto> getVehicleRatings(Long vehicleId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ratingRepository.findByVehicleId(vehicleId, pageable)
                .map(this::convertToResponseDto);
    }

    public List<RatingResponseDto> getRecentVehicleRatings(Long vehicleId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ratingRepository.findRecentReviewsByVehicleId(vehicleId, pageable)
                .stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    public VehicleRatingSummaryDto getVehicleRatingSummary(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // Get rating distribution
        List<Object[]> distribution = ratingRepository.getRatingDistributionForVehicle(vehicleId);
        Map<Integer, Integer> ratingMap = new HashMap<>();
        int rating5Count = 0, rating4Count = 0, rating3Count = 0, rating2Count = 0, rating1Count = 0;

        for (Object[] row : distribution) {
            Integer rating = ((Number) row[0]).intValue();
            Long count = ((Number) row[1]).longValue();
            ratingMap.put(rating, count.intValue());

            switch (rating) {
                case 5 -> rating5Count = count.intValue();
                case 4 -> rating4Count = count.intValue();
                case 3 -> rating3Count = count.intValue();
                case 2 -> rating2Count = count.intValue();
                case 1 -> rating1Count = count.intValue();
                default -> {}
            }
        }

        // Get recent reviews
        List<RatingResponseDto> recentReviews = getRecentVehicleRatings(vehicleId, 5);

        return VehicleRatingSummaryDto.builder()
                .vehicleId(vehicle.getId())
                .vehicleName(vehicle.getBrand() + " " + vehicle.getModel())
                .vehicleBrand(vehicle.getBrand())
                .vehicleModel(vehicle.getModel())
                .averageRating(vehicle.getAverageRating())
                .totalRatings(vehicle.getTotalRatings())
                .rating5Count(rating5Count)
                .rating4Count(rating4Count)
                .rating3Count(rating3Count)
                .rating2Count(rating2Count)
                .rating1Count(rating1Count)
                .ratingDistribution(ratingMap)
                .recentReviews(recentReviews)
                .build();
    }

    private RatingResponseDto convertToResponseDto(Rating rating) {
        return RatingResponseDto.builder()
                .id(rating.getId())
                .bookingId(rating.getBooking().getId())
                .vehicleId(rating.getVehicle().getId())
                .vehicleName(rating.getVehicle().getBrand() + " " + rating.getVehicle().getModel())
                .vehicleBrand(rating.getVehicle().getBrand())
                .vehicleModel(rating.getVehicle().getModel())
                .renterId(rating.getRenter().getId())
                .renterName(rating.getRenter().getFullName())
                .rating(rating.getRating())
                .review(rating.getReview())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}