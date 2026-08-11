// repository/VehicleRepository.java
package com.mobilityhub.repository;

import com.mobilityhub.model.User;
import com.mobilityhub.model.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Find by owner
    List<Vehicle> findByOwner(User owner);
    List<Vehicle> findByOwnerId(Long ownerId);
    Page<Vehicle> findByOwnerId(Long ownerId, Pageable pageable);

    // Find available vehicles
    List<Vehicle> findByIsAvailableTrue();
    Page<Vehicle> findByIsAvailableTrue(Pageable pageable);

    // Find verified vehicles
    List<Vehicle> findByIsVerifiedTrue();
    Page<Vehicle> findByIsVerifiedTrue(Pageable pageable);

    // Find unverified vehicles (for admin)
    List<Vehicle> findByIsVerifiedFalse();
    Page<Vehicle> findByIsVerifiedFalse(Pageable pageable);

    // ✅ ADD THIS METHOD - Find pending vehicles (unverified and not rejected)
    List<Vehicle> findByIsVerifiedFalseAndRejectionReasonIsNull();

    // Find vehicles by city
    Page<Vehicle> findByCityIgnoreCaseAndIsAvailableTrue(String city, Pageable pageable);

    // Find by license plate
    Optional<Vehicle> findByLicensePlate(String licensePlate);

    // Check existence by license plate
    boolean existsByLicensePlate(String licensePlate);

    // Search vehicles with filters
    @Query("SELECT v FROM Vehicle v WHERE " +
            "(:brand IS NULL OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :brand, '%'))) AND " +
            "(:model IS NULL OR LOWER(v.model) LIKE LOWER(CONCAT('%', :model, '%'))) AND " +
            "(:city IS NULL OR LOWER(v.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
            "(:fuelType IS NULL OR v.fuelType = :fuelType) AND " +
            "(:transmission IS NULL OR v.transmission = :transmission) AND " +
            "(:minSeats IS NULL OR v.seats >= :minSeats) AND " +
            "(:maxSeats IS NULL OR v.seats <= :maxSeats) AND " +
            "(:minPrice IS NULL OR v.pricePerDay >= :minPrice) AND " +
            "(:maxPrice IS NULL OR v.pricePerDay <= :maxPrice) AND " +
            "v.isAvailable = true AND v.isVerified = true")
    Page<Vehicle> searchVehicles(@Param("brand") String brand,
                                 @Param("model") String model,
                                 @Param("city") String city,
                                 @Param("fuelType") String fuelType,
                                 @Param("transmission") String transmission,
                                 @Param("minSeats") Integer minSeats,
                                 @Param("maxSeats") Integer maxSeats,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 Pageable pageable);

    // Count vehicles by owner
    long countByOwnerId(Long ownerId);

    // Find featured vehicles (most viewed/rented)
    @Query("SELECT v FROM Vehicle v WHERE v.isAvailable = true AND v.isVerified = true ORDER BY v.viewCount DESC")
    Page<Vehicle> findFeaturedVehicles(Pageable pageable);

    // Find recently added vehicles
    @Query("SELECT v FROM Vehicle v WHERE v.isAvailable = true AND v.isVerified = true ORDER BY v.createdAt DESC")
    Page<Vehicle> findRecentVehicles(Pageable pageable);

    // Find vehicles by city (simple)
    List<Vehicle> findByCityIgnoreCase(String city);

    // Find vehicles by brand
    List<Vehicle> findByBrandIgnoreCase(String brand);

    // Find vehicles by fuel type
    List<Vehicle> findByFuelTypeIgnoreCase(String fuelType);

    // Find vehicles by price range
    List<Vehicle> findByPricePerDayBetween(BigDecimal minPrice, BigDecimal maxPrice);

    // Find available vehicles in a city
    List<Vehicle> findByCityIgnoreCaseAndIsAvailableTrue(String city);

    // Count total vehicles by owner
    long countByOwner(User owner);
}