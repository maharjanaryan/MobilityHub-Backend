// service/AdminUserService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.AdminUpdateUserRequestDto;
import com.mobilityhub.dto.response.AdminUserResponseDto;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.dto.response.UserStatisticsDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RenterKycRepository renterKycRepository;
    private final OwnerKycRepository ownerKycRepository;

    /**
     * Get all users with pagination and filters
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponseDto> getAllUsers(int page, int size, String role, String search, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size);
        List<User> allUsers = userRepository.findAll();

        // Apply filters
        List<User> filteredUsers = allUsers.stream()
                .filter(user -> {
                    if (role != null && !role.isEmpty() && !role.equals("all")) {
                        if (!user.getRole().name().equals(role)) return false;
                    }
                    if (search != null && !search.isEmpty()) {
                        return user.getUsername().toLowerCase().contains(search.toLowerCase()) ||
                                user.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                                user.getFullName().toLowerCase().contains(search.toLowerCase());
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Apply sorting
        if ("desc".equalsIgnoreCase(sortDir)) {
            filteredUsers.sort((a, b) -> {
                if ("createdAt".equals(sortBy)) {
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                }
                return b.getId().compareTo(a.getId());
            });
        } else {
            filteredUsers.sort((a, b) -> {
                if ("createdAt".equals(sortBy)) {
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                }
                return a.getId().compareTo(b.getId());
            });
        }

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredUsers.size());
        List<User> paginatedUsers = filteredUsers.subList(start, end);

        List<AdminUserResponseDto> dtos = paginatedUsers.stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, filteredUsers.size());
    }

    /**
     * Get user by ID for admin
     */
    @Transactional(readOnly = true)
    public AdminUserResponseDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToAdminResponse(user);
    }

    /**
     * Update user by admin
     */
    @Transactional
    public MessageResponse updateUserByAdmin(Long userId, AdminUpdateUserRequestDto request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (request.getFullName() != null) {
                user.setFullName(request.getFullName());
            }
            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }
            if (request.getRole() != null) {
                user.setRole(Role.valueOf(request.getRole()));
            }
            if (request.getIsActive() != null) {
                user.setActive(request.getIsActive());
            }
            if (request.getEmailVerified() != null) {
                user.setEmailVerified(request.getEmailVerified());
            }

            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Admin updated user: {}", user.getUsername());

            return new MessageResponse("User updated successfully", true, null);

        } catch (Exception e) {
            log.error("Failed to update user: {}", e.getMessage());
            return new MessageResponse("Failed to update user: " + e.getMessage(), false, null);
        }
    }

    /**
     * Delete user (soft delete - deactivate)
     */
    @Transactional
    public MessageResponse deleteUser(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Prevent deleting the last admin
            if (user.getRole() == Role.ADMIN) {
                long adminCount = userRepository.findByRole(Role.ADMIN).size();
                if (adminCount <= 1) {
                    return new MessageResponse("Cannot delete the last admin user", false, null);
                }
            }

            // Soft delete - deactivate account
            user.setActive(false);
            userRepository.save(user);

            log.info("Admin deactivated user: {}", user.getUsername());

            return new MessageResponse("User deactivated successfully", true, null);

        } catch (Exception e) {
            return new MessageResponse("Failed to delete user: " + e.getMessage(), false, null);
        }
    }

    /**
     * Activate user account
     */
    @Transactional
    public MessageResponse activateUser(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setActive(true);
            userRepository.save(user);

            log.info("Admin activated user: {}", user.getUsername());

            return new MessageResponse("User activated successfully", true, null);

        } catch (Exception e) {
            return new MessageResponse("Failed to activate user: " + e.getMessage(), false, null);
        }
    }

    /**
     * Get user statistics for admin dashboard
     */
    public UserStatisticsDto getUserStatistics() {
        List<User> allUsers = userRepository.findAll();
        long totalUsers = allUsers.size();
        long activeUsers = allUsers.stream().filter(User::isActive).count();
        long adminUsers = allUsers.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        long ownerUsers = allUsers.stream().filter(u -> u.getRole() == Role.OWNER).count();
        long regularUsers = allUsers.stream().filter(u -> u.getRole() == Role.USER).count();
        long oAuthUsers = allUsers.stream().filter(User::isOAuthUser).count();
        long verifiedUsers = allUsers.stream().filter(User::isEmailVerified).count();

        return UserStatisticsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .adminUsers(adminUsers)
                .ownerUsers(ownerUsers)
                .regularUsers(regularUsers)
                .oAuthUsers(oAuthUsers)
                .verifiedUsers(verifiedUsers)
                .build();
    }

    private AdminUserResponseDto mapToAdminResponse(User user) {
        RenterKyc renterKyc = renterKycRepository.findByUserId(user.getId()).orElse(null);
        OwnerKyc ownerKyc = ownerKycRepository.findByUserId(user.getId()).orElse(null);

        return AdminUserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .provider(user.getProvider())
                .isOAuthUser(user.isOAuthUser())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .renterKycStatus(renterKyc != null ? renterKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .ownerKycStatus(ownerKyc != null ? ownerKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .build();
    }
}