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
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ── Core availability check ──────────────────────────────────────────────
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
    Page<Booking> findByRenterIdAndBookingStatus(Long renterId, Booking.BookingStatus status, Pageable pageable);

    // ── Owner queries ────────────────────────────────────────────────────────
    Page<Booking> findByOwnerId(Long ownerId, Pageable pageable);
    Page<Booking> findByOwnerIdAndBookingStatus(Long ownerId, Booking.BookingStatus status, Pageable pageable);
    List<Booking> findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(Long ownerId, Booking.BookingStatus status);

    // ── Vehicle queries ──────────────────────────────────────────────────────
    List<Booking> findByVehicleIdAndBookingStatusIn(Long vehicleId, List<Booking.BookingStatus> statuses);

    Optional<Booking> findByPaymentId(String paymentId);
    Optional<Booking> findByTransactionId(String transactionId);

    // ═══════════════════════════════════════════════════════════════════════════
    //  ADMIN METHODS - Only what's needed for the admin panel
    // ═══════════════════════════════════════════════════════════════════════════

    // 1. Count by status (for stats cards)
    long countByBookingStatus(Booking.BookingStatus status);

    // 2. Sum revenue by statuses (for total revenue)
    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.bookingStatus IN :statuses")
    Double sumTotalAmountByBookingStatusIn(@Param("statuses") List<Booking.BookingStatus> statuses);

    // 3. Admin filter queries (with pagination)
    Page<Booking> findByBookingStatusAndPaymentStatus(
            Booking.BookingStatus bookingStatus,
            Booking.PaymentStatus paymentStatus,
            Pageable pageable);

    Page<Booking> findByBookingStatus(
            Booking.BookingStatus bookingStatus,
            Pageable pageable);

    Page<Booking> findByPaymentStatus(
            Booking.PaymentStatus paymentStatus,
            Pageable pageable);
}