package com.mobilityhub.controller;

import com.mobilityhub.dto.request.LocationUpdateRequestDto;
import com.mobilityhub.dto.response.LocationResponseDto;
import com.mobilityhub.exception.LocationNotFoundException;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.LocationTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class LocationTrackingController {

    private final LocationTrackingService locationTrackingService;

    /**
     * Renter's device posts a location ping every ~10-15 seconds while the trip is ACTIVE.
     */
    @PostMapping("/{bookingId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LocationResponseDto> updateLocation(
            @PathVariable Long bookingId,
            @Valid @RequestBody LocationUpdateRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(
                locationTrackingService.updateLocation(userDetails.getId(), bookingId, request)
        );
    }

    /**
     * Latest position — used for the live map pin. Poll every 5-10s from the frontend.
     * Returns 404 (not 500) when no location has been recorded yet — this is a normal,
     * expected state right after a trip starts, before the first GPS ping arrives.
     */
    @GetMapping("/{bookingId}/latest")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LocationResponseDto> getLatestLocation(
            @PathVariable Long bookingId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        try {
            LocationResponseDto response = locationTrackingService.getLatestLocation(
                    bookingId, userDetails.getId(), isAdmin(authentication));
            return ResponseEntity.ok(response);
        } catch (LocationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Route trail so far — used to draw the path on the map.
     */
    @GetMapping("/{bookingId}/history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<LocationResponseDto>> getLocationHistory(
            @PathVariable Long bookingId,
            @RequestParam(defaultValue = "300") int limit,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(
                locationTrackingService.getLocationHistory(bookingId, userDetails.getId(), isAdmin(authentication), limit)
        );
    }

    /**
     * Admin — pull recent movement for a specific renter by user ID
     * (support/safety investigation).
     */
    @GetMapping("/admin/user/{userId}/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LocationResponseDto>> getRecentLocationsForUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(
                locationTrackingService.getRecentLocationsForUser(userId, limit)
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }
}