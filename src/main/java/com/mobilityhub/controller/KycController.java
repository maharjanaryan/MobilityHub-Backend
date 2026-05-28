// controller/KycController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.OwnerKycRequestDto;
import com.mobilityhub.dto.request.RenterKycRequestDto;
import com.mobilityhub.dto.response.KycResponseDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    // For users who want to BOOK vehicles
    @PostMapping("/renter")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycResponseDto> submitRenterKyc(
            @Valid @RequestBody RenterKycRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(kycService.submitRenterKyc(request, userDetails.getId()));
    }

    // For users who want to SHARE/LIST vehicles
    @PostMapping("/owner")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycResponseDto> submitOwnerKyc(
            @Valid @RequestBody OwnerKycRequestDto request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(kycService.submitOwnerKyc(request, userDetails.getId()));
    }

    // Get complete KYC status
    @GetMapping("/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycResponseDto> getKycStatus(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return ResponseEntity.ok(kycService.getCompleteKycStatus(userDetails.getId()));
    }
}