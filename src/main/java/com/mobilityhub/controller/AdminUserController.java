// controller/AdminUserController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.AdminUpdateUserRequestDto;
import com.mobilityhub.dto.response.AdminUserResponseDto;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.dto.response.UserStatisticsDto;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * Get all users with pagination and filters
     */
    @GetMapping
    public ResponseEntity<Page<AdminUserResponseDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        return ResponseEntity.ok(adminUserService.getAllUsers(page, size, role, search, sortBy, sortDir));
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponseDto> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserById(userId));
    }

    /**
     * Update user by admin
     */
    @PutMapping("/{userId}")
    public ResponseEntity<MessageResponse> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequestDto request) {
        return ResponseEntity.ok(adminUserService.updateUserByAdmin(userId, request));
    }

    /**
     * Delete/Deactivate user
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.deleteUser(userId));
    }

    /**
     * Activate user
     */
    @PostMapping("/{userId}/activate")
    public ResponseEntity<MessageResponse> activateUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.activateUser(userId));
    }

    /**
     * Get user statistics for dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<UserStatisticsDto> getUserStatistics() {
        return ResponseEntity.ok(adminUserService.getUserStatistics());
    }
}