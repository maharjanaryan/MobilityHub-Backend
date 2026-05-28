// controller/AdminKycController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.KycVerificationRequestDto;
import com.mobilityhub.dto.response.AdminKycResponseDto;
import com.mobilityhub.dto.response.KycDetailsResponseDto;
import com.mobilityhub.dto.response.KycResponseDto;
import com.mobilityhub.dto.response.KycStatisticsResponseDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.AdminKycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/kyc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKycController {

    private final AdminKycService adminKycService;

    /**
     * Get all pending renter KYC requests
     */
    @GetMapping("/pending/renters")
    public ResponseEntity<List<AdminKycResponseDto>> getPendingRenterKyc() {
        log.info("Admin fetching pending renter KYC requests");
        return ResponseEntity.ok(adminKycService.getPendingRenterKyc());
    }

    /**
     * Get all pending owner KYC requests
     */
    @GetMapping("/pending/owners")
    public ResponseEntity<List<AdminKycResponseDto>> getPendingOwnerKyc() {
        log.info("Admin fetching pending owner KYC requests");
        return ResponseEntity.ok(adminKycService.getPendingOwnerKyc());
    }

    /**
     * Get all pending KYC (both types)
     */
    @GetMapping("/pending/all")
    public ResponseEntity<List<AdminKycResponseDto>> getAllPendingKyc() {
        log.info("Admin fetching all pending KYC requests");
        return ResponseEntity.ok(adminKycService.getAllPendingKyc());
    }

    /**
     * Get KYC details by ID (for admin review) - Returns KycDetailsResponseDto
     */
    @GetMapping("/{kycId}")
    public ResponseEntity<KycDetailsResponseDto> getKycDetails(@PathVariable Long kycId) {
        log.info("Admin viewing KYC details for ID: {}", kycId);
        return ResponseEntity.ok(adminKycService.getKycDetails(kycId));
    }

    /**
     * Get KYC details by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getKycByUserId(@PathVariable Long userId) {
        log.info("Admin viewing KYC for user ID: {}", userId);
        return ResponseEntity.ok(adminKycService.getKycByUserId(userId));
    }

    /**
     * Verify Renter KYC (Approve or Reject)
     */
    @PostMapping("/verify/renter/{kycId}")
    public ResponseEntity<KycResponseDto> verifyRenterKyc(
            @PathVariable Long kycId,
            @RequestBody KycVerificationRequestDto request,
            Authentication authentication) {

        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} verifying renter KYC ID: {} with approval: {}",
                adminDetails.getUsername(), kycId, request.getApproved());

        KycResponseDto response = adminKycService.verifyRenterKyc(
                kycId,
                adminDetails.getId(),
                request.getApproved(),
                request.getRejectionReason(),
                request.getAdminNotes()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Owner KYC (Approve or Reject)
     */
    @PostMapping("/verify/owner/{kycId}")
    public ResponseEntity<KycResponseDto> verifyOwnerKyc(
            @PathVariable Long kycId,
            @RequestBody KycVerificationRequestDto request,
            Authentication authentication) {

        UserDetailsImpl adminDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Admin {} verifying owner KYC ID: {} with approval: {}",
                adminDetails.getUsername(), kycId, request.getApproved());

        KycResponseDto response = adminKycService.verifyOwnerKyc(
                kycId,
                adminDetails.getId(),
                request.getApproved(),
                request.getRejectionReason(),
                request.getAdminNotes()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Get KYC statistics for admin dashboard - Returns KycStatisticsResponseDto
     */
    @GetMapping("/statistics")
    public ResponseEntity<KycStatisticsResponseDto> getKycStatistics() {
        log.info("Admin fetching KYC statistics");
        return ResponseEntity.ok(adminKycService.getKycStatistics());
    }

    /**
     * Get all verified users (for admin reference)
     */
    @GetMapping("/verified/users")
    public ResponseEntity<List<AdminKycResponseDto>> getVerifiedUsers() {
        log.info("Admin fetching verified users");
        return ResponseEntity.ok(adminKycService.getVerifiedUsers());
    }

    /**
     * Get rejected KYC list
     */
    @GetMapping("/rejected")
    public ResponseEntity<List<AdminKycResponseDto>> getRejectedKyc() {
        log.info("Admin fetching rejected KYC requests");
        return ResponseEntity.ok(adminKycService.getRejectedKyc());
    }
}