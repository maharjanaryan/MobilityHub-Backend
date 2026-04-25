// service/AuthService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.LoginRequest;
import com.mobilityhub.dto.request.SignupRequest;
import com.mobilityhub.dto.request.VerifyCodeRequest;
import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.verification.code.expiration:600000}")
    private long verificationCodeExpiryMs;

    @Transactional
    public User registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }

        String verificationCode = generateVerificationCode();

        User user = User.builder()
                .username(signupRequest.getUsername())
                .email(signupRequest.getEmail())
                .fullName(signupRequest.getFullName())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .role(Role.USER)
                .isActive(true)
                .emailVerified(false)
                .verificationCode(verificationCode)
                .verificationCodeExpiry(LocalDateTime.now().plusMinutes(10)) // Changed: 10 minutes expiry
                .build();

        User savedUser = userRepository.save(user);
        emailService.sendVerificationCode(savedUser.getEmail(), savedUser.getUsername(), verificationCode);

        return savedUser;
    }

    @Transactional
    public User verifyEmail(VerifyCodeRequest verifyRequest) {
        User user = userRepository.findByEmail(verifyRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + verifyRequest.getEmail()));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified!");
        }

        if (!user.getVerificationCode().equals(verifyRequest.getVerificationCode())) {
            throw new RuntimeException("Invalid verification code!");
        }

        if (user.getVerificationCodeExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired! Please request a new code.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);

        return userRepository.save(user);
    }

    @Transactional
    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified!");
        }

        String newVerificationCode = generateVerificationCode();
        user.setVerificationCode(newVerificationCode);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10)); // Changed: 10 minutes expiry

        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getUsername(), newVerificationCode);
    }

    public Authentication authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<User> user = userRepository.findByUsername(loginRequest.getUsername());
        user.ifPresent(u -> {
            u.setLastLogin(LocalDateTime.now());
            userRepository.save(u);
        });

        return authentication;
    }

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}