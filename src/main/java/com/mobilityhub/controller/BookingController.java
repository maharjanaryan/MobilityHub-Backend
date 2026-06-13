package com.mobilityhub.controller;

import com.mobilityhub.dto.request.BookingActionRequestDto;
import com.mobilityhub.dto.request.BookingRequestDto;
import com.mobilityhub.dto.response.BookingResponseDto;
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

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        BookingResponseDto response = bookingService.createBooking(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> getBookingById(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(bookingService.getBookingById(bookingId, userDetails.getId(), role));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<Page<BookingResponseDto>> getMyBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getBookingsByRenter(userDetails.getId(), status, page, size));
    }

    @GetMapping("/owner-bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Page<BookingResponseDto>> getOwnerBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getBookingsByOwner(userDetails.getId(), status, page, size));
    }

    @GetMapping("/owner/pending")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<BookingResponseDto>> getPendingBookingsForOwner(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getPendingBookingsForOwner(userDetails.getId()));
    }

    @PostMapping("/{bookingId}/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BookingResponseDto> approveBooking(
            @PathVariable Long bookingId,
            @RequestBody BookingActionRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.approveBooking(bookingId, userDetails.getId(), request));
    }

    @PostMapping("/{bookingId}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<BookingResponseDto> rejectBooking(
            @PathVariable Long bookingId,
            @RequestBody BookingActionRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.rejectBooking(bookingId, userDetails.getId(), request));
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<BookingResponseDto> cancelBooking(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId, userDetails.getId()));
    }

    /**
     * Check vehicle availability for specific dates
     * Accepts date strings to avoid timezone conversion issues
     * Frontend sends: "2026-06-13T00:00:00.000Z"
     * We parse only the date part "2026-06-13" and use start of day
     */
    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam String pickupDate,    // "2026-06-13T00:00:00.000Z"
            @RequestParam String dropoffDate) { // "2026-06-16T00:00:00.000Z"

        try {
            // Parse date-only from ISO string, ignore time and timezone
            // Take only the first 10 characters: "YYYY-MM-DD"
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
}
