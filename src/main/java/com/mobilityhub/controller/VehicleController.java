package com.mobilityhub.controller;

import com.mobilityhub.dto.request.VehicleRequestDto;
import com.mobilityhub.dto.request.VehicleSearchRequestDto;
import com.mobilityhub.dto.response.VehicleResponseDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * Add new vehicle listing (Requires verified Owner KYC)
     */
    @PostMapping
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<VehicleResponseDto> addVehicle(
            @Valid @RequestBody VehicleRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        VehicleResponseDto response = vehicleService.addVehicle(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get vehicle by ID
     */
    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponseDto> getVehicleById(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleService.getVehicleById(vehicleId));
    }

    /**
     * Get all vehicles by current owner (Requires verified Owner KYC)
     */
    @GetMapping("/owner/my-vehicles")
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<List<VehicleResponseDto>> getMyVehicles(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleService.getVehiclesByOwner(userDetails.getId()));
    }

    /**
     * Get paginated vehicles by owner (Requires verified Owner KYC)
     */
    @GetMapping("/owner/my-vehicles/paginated")
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<Page<VehicleResponseDto>> getMyVehiclesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleService.getVehiclesByOwner(userDetails.getId(), page, size));
    }

    /**
     * Update vehicle (Requires verified Owner KYC)
     */
    @PutMapping("/{vehicleId}")
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<VehicleResponseDto> updateVehicle(
            @PathVariable Long vehicleId,
            @Valid @RequestBody VehicleRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleService.updateVehicle(vehicleId, userDetails.getId(), request));
    }

    /**
     * Delete vehicle (Requires verified Owner KYC)
     */
    @DeleteMapping("/{vehicleId}")
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<?> deleteVehicle(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        vehicleService.deleteVehicle(vehicleId, userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Vehicle deleted successfully"));
    }

    /**
     * Toggle vehicle availability (Requires verified Owner KYC)
     */
    @PatchMapping("/{vehicleId}/toggle-availability")
    @PreAuthorize("@vehicleService.canAddVehicle(#authentication.principal.id)")
    public ResponseEntity<VehicleResponseDto> toggleAvailability(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleService.toggleAvailability(vehicleId, userDetails.getId()));
    }

    /**
     * Search vehicles (Public)
     */
    @PostMapping("/search")
    public ResponseEntity<Page<VehicleResponseDto>> searchVehicles(
            @RequestBody VehicleSearchRequestDto request) {
        return ResponseEntity.ok(vehicleService.searchVehicles(request));
    }

    /**
     * Get featured vehicles (Public)
     */
    @GetMapping("/featured")
    public ResponseEntity<Page<VehicleResponseDto>> getFeaturedVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(vehicleService.getFeaturedVehicles(page, size));
    }

    /**
     * Get recent vehicles (Public)
     */
    @GetMapping("/recent")
    public ResponseEntity<Page<VehicleResponseDto>> getRecentVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(vehicleService.getRecentVehicles(page, size));
    }

    /**
     * Admin - Get pending vehicles for verification
     */
    @GetMapping("/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VehicleResponseDto>> getPendingVehicles() {
        return ResponseEntity.ok(vehicleService.getPendingVehicles());
    }

    /**
     * Admin - Verify vehicle
     */
    @PostMapping("/admin/verify/{vehicleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleResponseDto> verifyVehicle(
            @PathVariable Long vehicleId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String rejectionReason,
            Authentication authentication) {
        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(vehicleService.verifyVehicle(vehicleId, adminDetails.getId(), approved, rejectionReason));
    }
}