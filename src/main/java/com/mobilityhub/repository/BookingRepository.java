package com.mobilityhub.repository;

import com.mobilityhub.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ── Core availability check ──────────────────────────────────────────────
    // Only CONFIRMED and ACTIVE bookings block a vehicle.
    // Overlap condition: start1 < end2 AND end1 > start2
    // String literals required for Hibernate 6 — fully-qualified enum paths
    // are not supported in JPQL IN clauses in this version.
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.vehicle.id = :vehicleId
              AND b.bookingStatus IN ('CONFIRMED', 'ACTIVE')
              AND b.pickupDate  < :dropoffDate
              AND b.dropoffDate > :pickupDate
            """)
    boolean isVehicleBooked(
            @Param("vehicleId")   Long vehicleId,
            @Param("pickupDate")  LocalDateTime pickupDate,
            @Param("dropoffDate") LocalDateTime dropoffDate
    );

    // ── Renter queries ───────────────────────────────────────────────────────
    Page<Booking> findByRenterId(Long renterId, Pageable pageable);

    Page<Booking> findByRenterIdAndBookingStatus(
            Long renterId, Booking.BookingStatus status, Pageable pageable);

    // ── Owner queries ────────────────────────────────────────────────────────
    Page<Booking> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Booking> findByOwnerIdAndBookingStatus(
            Long ownerId, Booking.BookingStatus status, Pageable pageable);

    List<Booking> findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(
            Long ownerId, Booking.BookingStatus status);

    // ── Vehicle queries ──────────────────────────────────────────────────────
    List<Booking> findByVehicleIdAndBookingStatusIn(
            Long vehicleId, List<Booking.BookingStatus> statuses);
}