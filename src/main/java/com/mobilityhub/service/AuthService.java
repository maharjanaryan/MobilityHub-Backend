// service/AuthService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.LoginRequest;
import com.mobilityhub.dto.request.SignupRequest;
import com.mobilityhub.dto.request.VerifyCodeRequest;
import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.verification.code.expiration:600000}")
    private long verificationCodeExpiryMs;

    // Admin configuration from application.properties
    @Value("${app.admin.email:admin@gmail.com}")
    private String adminEmail;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin123!}")
    private String adminPassword;

    @Value("${app.admin.full-name:System Administrator}")
    private String adminFullName;

    /**
     * Initialize admin user on application startup
     * This runs automatically when the Spring Boot application starts
     */
    @PostConstruct
    public void initializeAdminUser() {
        createAdminUserIfNotExists();
    }

    /**
     * Create admin user if it doesn't exist in the database
     * Admin user is created with email_verified = true (no email verification needed)
     */
    private void createAdminUserIfNotExists() {
        try {
            Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);

            if (existingAdmin.isEmpty()) {
                // Create new admin user
                User admin = User.builder()
                        .username(adminUsername)
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .fullName(adminFullName)
                        .role(Role.ADMIN)
                        .isActive(true)
                        .emailVerified(true)  // CRITICAL: Admin doesn't need email verification
                        .verificationCode(null)
                        .verificationCodeExpiry(null)
                        .build();

                userRepository.save(admin);

                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("✅ DEFAULT ADMIN USER CREATED SUCCESSFULLY!");
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("🔐 ADMIN CREDENTIALS:");
                log.info("   • Email:    {}", adminEmail);
                log.info("   • Username: {}", adminUsername);
                log.info("   • Password: {}", adminPassword);
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.info("⚠️  IMPORTANT: Please change the default password after first login!");
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                User admin = existingAdmin.get();
                // Ensure existing user has ADMIN role
                if (admin.getRole() != Role.ADMIN) {
                    admin.setRole(Role.ADMIN);
                    admin.setEmailVerified(true); // Ensure admin is verified
                    userRepository.save(admin);
                    log.info("✅ Updated existing user '{}' to ADMIN role", admin.getUsername());
                } else {
                    // Ensure admin has email_verified = true
                    if (!admin.isEmailVerified()) {
                        admin.setEmailVerified(true);
                        userRepository.save(admin);
                        log.info("✅ Set email_verified=true for existing admin: {}", admin.getEmail());
                    }
                    log.info("✅ Admin user already exists with email: {}", adminEmail);
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize admin user: {}", e.getMessage());
        }
    }

    /**
     * Register a new regular user
     * Regular users get ROLE_USER and need email verification
     */
    @Transactional
    public User registerUser(SignupRequest signupRequest) {
        // Check if username exists
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        // Check if email exists
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }

        // Generate 6-digit verification code
        String verificationCode = generateVerificationCode();

        // Create new user with USER role
        User user = User.builder()
                .username(signupRequest.getUsername())
                .email(signupRequest.getEmail())
                .fullName(signupRequest.getFullName())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .role(Role.USER)  // Default role for new users
                .isActive(true)
                .emailVerified(false)  // Regular users need email verification
                .verificationCode(verificationCode)
                .verificationCodeExpiry(LocalDateTime.now().plusMinutes(10))
                .build();

        User savedUser = userRepository.save(user);

        // Send verification email (optional - can be disabled if no email service)
        try {
            emailService.sendVerificationCode(savedUser.getEmail(), savedUser.getUsername(), verificationCode);
            log.info("📧 Verification email sent to: {}", savedUser.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send verification email: {}", e.getMessage());
            // Don't throw exception - user can still be created
        }

        log.info("✅ New user registered: {} ({})", savedUser.getUsername(), savedUser.getEmail());

        return savedUser;
    }

    /**
     * Verify user's email with verification code
     */
    @Transactional
    public User verifyEmail(VerifyCodeRequest verifyRequest) {
        User user = userRepository.findByEmail(verifyRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + verifyRequest.getEmail()));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified!");
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(verifyRequest.getVerificationCode())) {
            throw new RuntimeException("Invalid verification code!");
        }

        if (user.getVerificationCodeExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired! Please request a new code.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);

        User verifiedUser = userRepository.save(user);
        log.info("✅ Email verified for user: {}", verifiedUser.getEmail());

        return verifiedUser;
    }

    /**
     * Resend verification code to user's email
     */
    @Transactional
    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified!");
        }

        String newVerificationCode = generateVerificationCode();
        user.setVerificationCode(newVerificationCode);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));

        userRepository.save(user);

        try {
            emailService.sendVerificationCode(user.getEmail(), user.getUsername(), newVerificationCode);
            log.info("📧 Resent verification code to: {}", email);
        } catch (Exception e) {
            log.warn("Failed to resend verification email: {}", e.getMessage());
        }
    }

    /**
     * Authenticate user and return Authentication object
     * This method handles both admin and regular users
     * Updated to accept email as the login identifier
     */
    public Authentication authenticateUser(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,  // Use email as the principal
                        password
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Update last login timestamp - find by email
        Optional<User> user = userRepository.findByEmail(email);
        user.ifPresent(u -> {
            u.setLastLogin(LocalDateTime.now());
            userRepository.save(u);
            log.info("🔐 User logged in: {} (Role: {})", u.getEmail(), u.getRole().name());
        });

        return authentication;
    }

    /**
     * Check if user needs email verification
     * Admin users don't need email verification
     */
    public boolean needsEmailVerification(User user) {
        return !user.isEmailVerified() && user.getRole() != Role.ADMIN;
    }

    /**
     * Generate a random 6-digit verification code
     */
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    /**
     * Check if user is admin by username
     */
    public boolean isAdmin(String username) {
        Optional<User> user = findByUsername(username);
        return user.map(this::isAdmin).orElse(false);
    }
}