package com.mobilityhub.controller;

import com.mobilityhub.dto.request.BookingActionRequestDto;
import com.mobilityhub.dto.request.BookingRequestDto;
import com.mobilityhub.dto.response.BookingResponseDto;
import com.mobilityhub.dto.response.VehicleAvailabilityStatusDto;
import com.mobilityhub.dto.response.VehicleBookingStatusDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class BookingController {

    private final BookingService bookingService;

    // ─────────────────────────────────────────────
    // CREATE BOOKING
    // ─────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        BookingResponseDto response = bookingService.createBooking(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────
    // GET BOOKINGS
    // ─────────────────────────────────────────────

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> getBookingById(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(bookingService.getBookingById(bookingId, userDetails.getId(), role));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<BookingResponseDto>> getMyBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getBookingsByRenter(userDetails.getId(), status, page, size));
    }

    @GetMapping("/owner-bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<BookingResponseDto>> getOwnerBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getBookingsByOwner(userDetails.getId(), status, page, size));
    }

    @GetMapping("/owner/pending")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<BookingResponseDto>> getPendingBookingsForOwner(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getPendingBookingsForOwner(userDetails.getId()));
    }

    // ─────────────────────────────────────────────
    // OWNER VEHICLE STATUS ENDPOINTS
    // ─────────────────────────────────────────────

    @GetMapping("/owner/vehicles/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<VehicleBookingStatusDto>> getOwnersVehicleBookingStatus(
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching vehicle booking status for owner: {}", userDetails.getId());
        return ResponseEntity.ok(bookingService.getOwnersBookedVehicles(userDetails.getId()));
    }

    @GetMapping("/owner/vehicles/availability")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<VehicleAvailabilityStatusDto>> getOwnersVehicleAvailability(
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching vehicle availability for owner: {}", userDetails.getId());
        return ResponseEntity.ok(bookingService.getOwnersVehicleAvailability(userDetails.getId()));
    }

    @GetMapping("/owner/vehicle/{vehicleId}/bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<BookingResponseDto>> getOwnerVehicleBookings(
            @PathVariable Long vehicleId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching bookings for owner: {}, vehicle: {}, status: {}",
                userDetails.getId(), vehicleId, status);
        return ResponseEntity.ok(bookingService.getBookingsByOwnerAndVehicle(
                userDetails.getId(), vehicleId, status, page, size));
    }

    @GetMapping("/owner/dashboard/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getOwnerDashboardSummary(
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching dashboard summary for owner: {}", userDetails.getId());
        return ResponseEntity.ok(bookingService.getOwnerBookingSummary(userDetails.getId()));
    }

    // ─────────────────────────────────────────────
    // BOOKING ACTIONS (APPROVE / REJECT / CANCEL)
    // ─────────────────────────────────────────────

    @PostMapping("/{bookingId}/approve")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> approveBooking(
            @PathVariable Long bookingId,
            @RequestBody(required = false) BookingActionRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (request == null) {
            request = new BookingActionRequestDto();
        }
        return ResponseEntity.ok(bookingService.approveBooking(bookingId, userDetails.getId(), request));
    }

    @PostMapping("/{bookingId}/reject")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> rejectBooking(
            @PathVariable Long bookingId,
            @RequestBody BookingActionRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.rejectBooking(bookingId, userDetails.getId(), request));
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId, userDetails.getId()));
    }

    // ─────────────────────────────────────────────
    // TRIP MANAGEMENT (START / END TRIP)
    // ─────────────────────────────────────────────

    @PostMapping("/{bookingId}/start-trip")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> startTrip(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("User {} starting trip for booking {}", userDetails.getId(), bookingId);
        return ResponseEntity.ok(bookingService.startTrip(bookingId, userDetails.getId()));
    }

    @PostMapping("/{bookingId}/end-trip")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BookingResponseDto> endTrip(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("User {} ending trip for booking {}", userDetails.getId(), bookingId);
        return ResponseEntity.ok(bookingService.endTrip(bookingId, userDetails.getId()));
    }

    // ─────────────────────────────────────────────
    // AVAILABILITY
    // ─────────────────────────────────────────────

    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam String pickupDate,
            @RequestParam String dropoffDate) {

        try {
            LocalDate pickupLocalDate = LocalDate.parse(pickupDate.substring(0, 10));
            LocalDate dropoffLocalDate = LocalDate.parse(dropoffDate.substring(0, 10));

            LocalDateTime pickup = pickupLocalDate.atStartOfDay();
            LocalDateTime dropoff = dropoffLocalDate.atStartOfDay();

            boolean isAvailable = bookingService.checkAvailability(vehicleId, pickup, dropoff);
            return ResponseEntity.ok(Map.of("available", isAvailable));

        } catch (Exception e) {
            log.error("Error parsing dates for availability check: pickupDate={}, dropoffDate={}",
                    pickupDate, dropoffDate, e);
            return ResponseEntity.badRequest().body(Map.of("available", false));
        }
    }

    @GetMapping("/vehicle/{vehicleId}/booked-dates")
    public ResponseEntity<List<LocalDateTime>> getBookedDates(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(bookingService.getBookedDatesForVehicle(vehicleId));
    }

    // ─────────────────────────────────────────────
    // ADMIN ENDPOINTS
    // ─────────────────────────────────────────────

    @GetMapping("/admin/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<BookingResponseDto>> getAllBookingsForAdmin(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} fetching all bookings with status: {}, paymentStatus: {}, page: {}, size: {}",
                adminDetails.getUsername(), status, paymentStatus, page, size);

        return ResponseEntity.ok(bookingService.getAllBookingsForAdmin(status, paymentStatus, page, size));
    }

    @GetMapping("/admin/bookings/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBookingStats(Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} fetching booking statistics", adminDetails.getUsername());

        return ResponseEntity.ok(bookingService.getBookingStats());
    }

    @PostMapping("/admin/bookings/{bookingId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> adminApproveBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} approving booking {}", adminDetails.getUsername(), bookingId);

        return ResponseEntity.ok(bookingService.adminApproveBooking(bookingId));
    }

    @PostMapping("/admin/bookings/{bookingId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> adminRejectBooking(
            @PathVariable Long bookingId,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        String reason = request != null ? request.get("reason") : null;
        log.info("Admin {} rejecting booking {} with reason: {}", adminDetails.getUsername(), bookingId, reason);

        return ResponseEntity.ok(bookingService.adminRejectBooking(bookingId, reason));
    }

    @PostMapping("/admin/bookings/{bookingId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> adminCancelBooking(
            @PathVariable Long bookingId,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        String reason = request != null ? request.get("reason") : null;
        log.info("Admin {} cancelling booking {} with reason: {}", adminDetails.getUsername(), bookingId, reason);

        return ResponseEntity.ok(bookingService.adminCancelBooking(bookingId, reason));
    }

    @PostMapping("/admin/bookings/{bookingId}/ongoing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> adminMarkAsOngoing(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} marking booking {} as ongoing", adminDetails.getUsername(), bookingId);

        return ResponseEntity.ok(bookingService.adminMarkAsOngoing(bookingId));
    }

    @PostMapping("/admin/bookings/{bookingId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> adminMarkAsCompleted(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} marking booking {} as completed", adminDetails.getUsername(), bookingId);

        return ResponseEntity.ok(bookingService.adminMarkAsCompleted(bookingId));
    }
}