package com.mobilityhub.service;

import com.mobilityhub.dto.request.LocationUpdateRequestDto;
import com.mobilityhub.dto.response.LocationResponseDto;
import com.mobilityhub.exception.LocationNotFoundException;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.LocationTracking;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.LocationTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationTrackingService {

    private final LocationTrackingRepository locationTrackingRepository;
    private final BookingRepository bookingRepository;

    /**
     * The renter's device calls this periodically (every ~10-15s) once the trip
     * has been started. Only the renter on the booking may push updates, and
     * only while the booking is ACTIVE.
     */
    @Transactional
    public LocationResponseDto updateLocation(Long userId, Long bookingId, LocationUpdateRequestDto request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getRenter().getId().equals(userId)) {
            throw new RuntimeException("Only the renter can send location updates for this trip");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.ACTIVE) {
            throw new RuntimeException("Location tracking is only available once the trip has started. Current status: " + booking.getBookingStatus());
        }

        LocationTracking location = LocationTracking.builder()
                .booking(booking)
                .renter(booking.getRenter())
                .latitude(BigDecimal.valueOf(request.getLatitude()))
                .longitude(BigDecimal.valueOf(request.getLongitude()))
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .accuracy(request.getAccuracy())
                .build();

        LocationTracking saved = locationTrackingRepository.save(location);
        log.info("Location update saved for booking {} by renter {}", bookingId, userId);
        return toResponseDto(saved);
    }

    /**
     * Latest position for a booking's live pin.
     * Allowed for: the renter, the vehicle owner, or an admin.
     * Throws LocationNotFoundException (mapped to 404, not 500) when no ping
     * has arrived yet — this is a normal state right after a trip starts.
     */
    public LocationResponseDto getLatestLocation(Long bookingId, Long requesterId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        assertCanView(booking, requesterId, isAdmin);

        LocationTracking latest = locationTrackingRepository
                .findTopByBookingIdOrderByRecordedAtDesc(bookingId)
                .orElseThrow(() -> new LocationNotFoundException("No location data available yet for this trip"));

        return toResponseDto(latest);
    }

    /**
     * Route trail (the path so far) for a booking. Returns an empty list
     * (not an error) if nothing has been recorded yet.
     */
    public List<LocationResponseDto> getLocationHistory(Long bookingId, Long requesterId, boolean isAdmin, int limit) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        assertCanView(booking, requesterId, isAdmin);

        return locationTrackingRepository
                .findByBookingIdOrderByRecordedAtAsc(bookingId, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Admin/support tool: pull recent movement for a specific renter by user ID,
     * across whichever trip(s) they've been on.
     */
    public List<LocationResponseDto> getRecentLocationsForUser(Long renterId, int limit) {
        return locationTrackingRepository
                .findByRenterIdOrderByRecordedAtDesc(renterId, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    private void assertCanView(Booking booking, Long requesterId, boolean isAdmin) {
        if (isAdmin) return;

        boolean isRenter = booking.getRenter().getId().equals(requesterId);
        boolean isOwner = booking.getOwner().getId().equals(requesterId);

        if (!isRenter && !isOwner) {
            throw new RuntimeException("You don't have permission to view this trip's location");
        }
    }

    private LocationResponseDto toResponseDto(LocationTracking location) {
        Booking booking = location.getBooking();
        return LocationResponseDto.builder()
                .bookingId(booking.getId())
                .renterId(location.getRenter().getId())
                .renterName(location.getRenter().getFullName())
                .vehicleId(booking.getVehicle().getId())
                .vehicleName(booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .speed(location.getSpeed())
                .heading(location.getHeading())
                .accuracy(location.getAccuracy())
                .recordedAt(location.getRecordedAt())
                .build();
    }
}