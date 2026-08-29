package com.mobilityhub.repository;

import com.mobilityhub.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByBookingId(Long bookingId);

    List<Rating> findByVehicleId(Long vehicleId);

    Page<Rating> findByVehicleId(Long vehicleId, Pageable pageable);

    @Query("SELECT r FROM Rating r WHERE r.vehicle.id = :vehicleId ORDER BY r.createdAt DESC")
    List<Rating> findRecentReviewsByVehicleId(@Param("vehicleId") Long vehicleId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.vehicle.id = :vehicleId")
    Double getAverageRatingForVehicle(@Param("vehicleId") Long vehicleId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.vehicle.id = :vehicleId")
    Integer getTotalRatingsForVehicle(@Param("vehicleId") Long vehicleId);

    @Query("SELECT r.rating, COUNT(r) FROM Rating r WHERE r.vehicle.id = :vehicleId GROUP BY r.rating")
    List<Object[]> getRatingDistributionForVehicle(@Param("vehicleId") Long vehicleId);

    boolean existsByBookingId(Long bookingId);
}