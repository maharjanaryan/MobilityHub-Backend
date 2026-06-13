// service/UserService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.ChangePasswordRequestDto;
import com.mobilityhub.dto.request.UpdateProfileRequestDto;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.dto.response.UserProfileResponseDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RenterKycRepository renterKycRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponseDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        RenterKyc renterKyc = renterKycRepository.findByUserId(userId).orElse(null);
        OwnerKyc ownerKyc = ownerKycRepository.findByUserId(userId).orElse(null);

        // Priority: Base64 > URL > default logo
        String avatar;
        if (user.getAvatarBase64() != null && !user.getAvatarBase64().isEmpty()) {
            avatar = user.getAvatarBase64();
        } else if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            avatar = user.getAvatarUrl();
        } else {
            avatar = "/logo.png";
        }

        return UserProfileResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(avatar)
                .role(user.getRole().name())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .renterKycStatus(renterKyc != null ? renterKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .ownerKycStatus(ownerKyc != null ? ownerKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .canBook(renterKyc != null && renterKyc.getKycStatus() == RenterKyc.KycStatus.VERIFIED)
                .canList(ownerKyc != null && ownerKyc.getKycStatus() == OwnerKyc.KycStatus.VERIFIED)
                .build();
    }

    @Transactional
    public MessageResponse updateUserProfile(Long userId, UpdateProfileRequestDto request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!user.getEmail().equals(request.getEmail()) &&
                    userRepository.existsByEmail(request.getEmail())) {
                return new MessageResponse("Email is already taken", false);
            }

            user.setFullName(request.getFullName());
            if (request.getFirstName() != null) {
                user.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                user.setLastName(request.getLastName());
            }
            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }
            user.setEmail(request.getEmail());
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);

            log.info("User profile updated for: {}", user.getUsername());

            return new MessageResponse("Profile updated successfully", true);

        } catch (Exception e) {
            log.error("Failed to update profile: {}", e.getMessage());
            return new MessageResponse("Failed to update profile: " + e.getMessage(), false);
        }
    }

    @Transactional
    public MessageResponse changePassword(Long userId, ChangePasswordRequestDto request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.isOAuthUser()) {
                return new MessageResponse("Google login users cannot change password. Use Google to login.", false);
            }

            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return new MessageResponse("Current password is incorrect", false);
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return new MessageResponse("New passwords do not match", false);
            }

            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                return new MessageResponse("New password cannot be the same as old password", false);
            }

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Password changed for user: {}", user.getUsername());

            return new MessageResponse("Password changed successfully", true);

        } catch (Exception e) {
            log.error("Failed to change password: {}", e.getMessage());
            return new MessageResponse("Failed to change password: " + e.getMessage(), false);
        }
    }

    @Transactional
    public MessageResponse updateAvatar(Long userId, String avatarUrl) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                return new MessageResponse("Avatar URL cannot be empty", false);
            }

            // Clear Base64 when using URL
            user.setAvatarBase64(null);
            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Avatar updated via URL for user: {}", user.getUsername());

            return new MessageResponse("Avatar updated successfully", true, avatarUrl);

        } catch (Exception e) {
            log.error("Failed to update avatar: {}", e.getMessage());
            return new MessageResponse("Failed to update avatar: " + e.getMessage(), false);
        }
    }

    /**
     * Upload avatar as Base64 - NO FILE CONTROLLER NEEDED!
     */
    @Transactional
    public MessageResponse uploadAvatar(Long userId, MultipartFile file) {
        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                return new MessageResponse("Please select a file to upload", false);
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return new MessageResponse("Only image files are allowed", false);
            }

            // Validate specific image types
            if (!contentType.equals("image/jpeg") && !contentType.equals("image/jpg") &&
                    !contentType.equals("image/png") && !contentType.equals("image/gif") &&
                    !contentType.equals("image/webp")) {
                return new MessageResponse("Only JPEG, PNG, GIF, and WEBP images are allowed", false);
            }

            // Validate file size (max 5MB)
            long maxSize = 5 * 1024 * 1024; // 5MB
            if (file.getSize() > maxSize) {
                return new MessageResponse("File size must be less than 5MB", false);
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Convert to Base64
            byte[] bytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(bytes);

            // Store with data URL prefix for direct use in img src
            String avatarDataUrl = "data:" + contentType + ";base64," + base64Image;

            // Save Base64 to database (clear URL)
            user.setAvatarBase64(avatarDataUrl);
            user.setAvatarUrl(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Avatar uploaded as Base64 for user: {}. Size: {} bytes", user.getUsername(), bytes.length);

            return new MessageResponse("Avatar uploaded successfully", true, avatarDataUrl);

        } catch (IOException e) {
            log.error("Failed to upload avatar - IO error: {}", e.getMessage(), e);
            return new MessageResponse("Failed to upload avatar: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to upload avatar: {}", e.getMessage(), e);
            return new MessageResponse("Failed to upload avatar: " + e.getMessage(), false);
        }
    }

    /**
     * Delete user avatar
     */
    @Transactional
    public MessageResponse deleteAvatar(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setAvatarBase64(null);
            user.setAvatarUrl(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Avatar deleted for user: {}", user.getUsername());

            return new MessageResponse("Avatar deleted successfully", true);

        } catch (Exception e) {
            log.error("Failed to delete avatar: {}", e.getMessage(), e);
            return new MessageResponse("Failed to delete avatar: " + e.getMessage(), false);
        }
    }

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        User user = getUserById(userId);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }
}