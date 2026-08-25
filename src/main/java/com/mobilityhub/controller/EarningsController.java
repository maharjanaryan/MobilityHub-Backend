// com/mobilityhub/controller/EarningsController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.response.EarningsSummaryDto;
import com.mobilityhub.dto.response.EarningsTransactionDto;
import com.mobilityhub.dto.response.MonthlyEarningsDto;
import com.mobilityhub.dto.response.VehicleEarningsDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/earnings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class EarningsController {

    private final EarningsService earningsService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<EarningsSummaryDto> getEarningsSummary(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching earnings summary for owner: {}", userDetails.getId());
        return ResponseEntity.ok(earningsService.getEarningsSummary(userDetails.getId()));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<Page<EarningsTransactionDto>> getEarningsTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching earnings transactions for owner: {}", userDetails.getId());

        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        return ResponseEntity.ok(earningsService.getEarningsTransactions(
                userDetails.getId(), type, status, startDate, endDate, pageable));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<List<MonthlyEarningsDto>> getMonthlyEarnings(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        int targetYear = year != null ? year : LocalDate.now().getYear();
        log.info("Fetching monthly earnings for owner: {}, year: {}", userDetails.getId(), targetYear);
        return ResponseEntity.ok(earningsService.getMonthlyEarnings(userDetails.getId(), targetYear));
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<List<VehicleEarningsDto>> getVehicleEarnings(
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Fetching vehicle earnings for owner: {}", userDetails.getId());
        return ResponseEntity.ok(earningsService.getVehicleEarnings(userDetails.getId()));
    }

    @GetMapping("/chart-data")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getChartData(
            @RequestParam(required = false) String period,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String periodType = period != null ? period : "6months";
        log.info("Fetching chart data for owner: {}, period: {}", userDetails.getId(), periodType);
        return ResponseEntity.ok(earningsService.getChartData(userDetails.getId(), periodType));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER')")
    public ResponseEntity<byte[]> exportEarnings(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Exporting earnings for owner: {}", userDetails.getId());
        return earningsService.exportEarnings(userDetails.getId(), startDate, endDate);
    }
}