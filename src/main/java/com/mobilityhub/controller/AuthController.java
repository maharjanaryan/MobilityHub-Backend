// controller/AuthController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.*;
import com.mobilityhub.dto.response.JwtResponse;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.model.RefreshToken;
import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.security.jwt.JwtUtils;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.AuthService;
import com.mobilityhub.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        log.info("=== SIGNUP REQUEST RECEIVED ===");
        log.info("Username: {}", signupRequest.getUsername());
        log.info("Email: {}", signupRequest.getEmail());
        log.info("Full Name: {}", signupRequest.getFullName());

        try {
            User user = authService.registerUser(signupRequest);
            return ResponseEntity.ok(new MessageResponse(
                    "User registered successfully! Please check your email for verification code.",
                    true,
                    user.getEmail()));
        } catch (RuntimeException e) {
            log.error("Signup error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false, null));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyCodeRequest verifyRequest) {
        try {
            User user = authService.verifyEmail(verifyRequest);
            return ResponseEntity.ok(new MessageResponse(
                    "Email verified successfully! You can now login.",
                    true,
                    user));
        } catch (RuntimeException e) {
            log.error("Email verification error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false, null));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendCodeRequest request) {
        try {
            authService.resendVerificationCode(request.getEmail());
            return ResponseEntity.ok(new MessageResponse(
                    "Verification code sent to your email!",
                    true,
                    null));
        } catch (RuntimeException e) {
            log.error("Resend verification error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false, null));
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.info("=== LOGIN REQUEST RECEIVED ===");
            log.info("Email: {}", loginRequest.getEmail());

            // Find user by email
            User user = authService.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + loginRequest.getEmail()));

            log.info("User found: {} (Username: {}, Role: {}, Email Verified: {})",
                    user.getEmail(), user.getUsername(), user.getRole(), user.isEmailVerified());

            // CRITICAL: Skip email verification for ADMIN users
            // Admin can login without email verification
            if (!user.isEmailVerified() && user.getRole() != Role.ADMIN) {
                log.warn("Login blocked: User {} has not verified email", user.getEmail());
                return ResponseEntity.status(403).body(new MessageResponse(
                        "Please verify your email before logging in!",
                        false,
                        null));
            }

            // Authenticate user with email and password
            Authentication authentication = authService.authenticateUser(loginRequest.getEmail(), loginRequest.getPassword());

            // Generate tokens
            String jwt = jwtUtils.generateJwtToken(authentication);
            String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(userDetails.getId());

            // Get role name (remove "ROLE_" prefix if present)
            String roleName = user.getRole().name();

            log.info("Login successful: {} (Role: {})", user.getEmail(), roleName);

            // Return response WITH ROLE for frontend redirection
            return ResponseEntity.ok(JwtResponse.builder()
                    .accessToken(jwt)
                    .refreshToken(refreshTokenEntity.getToken())
                    .tokenType("Bearer")
                    .id(userDetails.getId())
                    .username(userDetails.getUsername())
                    .email(userDetails.getEmail())
                    .fullName(userDetails.getFullName())
                    .role(roleName)  // IMPORTANT: Send role to frontend
                    .expiresIn(86400000L)
                    .build());

        } catch (RuntimeException e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage(), false, null));
        }
    }

    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateTokenFromUsername(user.getUsername(), 86400000);
                    log.info("Token refreshed for user: {}", user.getUsername());
                    return ResponseEntity.ok(JwtResponse.builder()
                            .accessToken(token)
                            .refreshToken(requestRefreshToken)
                            .tokenType("Bearer")
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .role(user.getRole().name())
                            .expiresIn(86400000L)
                            .build());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            refreshTokenService.deleteByUserId(userDetails.getId());
            log.info("User logged out: {}", userDetails.getUsername());
        }
        return ResponseEntity.ok(new MessageResponse("Logout successful!", true, null));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(new MessageResponse("Unauthorized", false, null));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("Current user retrieved: {}", userDetails.getUsername());
        return ResponseEntity.ok(userDetails);
    }
}