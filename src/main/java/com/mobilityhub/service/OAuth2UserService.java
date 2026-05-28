// service/OAuth2UserService.java
package com.mobilityhub.service;

import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("=== OAuth2 loadUser START ===");

        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String firstName = (String) attributes.get("given_name");
        String lastName = (String) attributes.get("family_name");
        String fullName = (String) attributes.get("name");
        String providerId = (String) attributes.get("sub");
        String avatarUrl = (String) attributes.get("picture");
        String provider = userRequest.getClientRegistration().getRegistrationId();

        log.info("OAuth2 User info - Email: {}, FirstName: {}, LastName: {}", email, firstName, lastName);

        // Find or create user (always with USER role)
        User user = findOrCreateUser(email, firstName, lastName, fullName, providerId, provider, avatarUrl);

        log.info("OAuth2 user authenticated: {} with role: {}", user.getUsername(), user.getRole());

        // Return OAuth2User with USER authority
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "email"
        );
    }

    private User findOrCreateUser(String email, String firstName, String lastName, String fullName,
                                  String providerId, String provider, String avatarUrl) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // Update existing user with OAuth info if needed
            boolean updated = false;

            if (user.getFirstName() == null) {
                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setAvatarUrl(avatarUrl);
                user.setProvider(provider);
                user.setProviderId(providerId);
                user.setOAuthUser(true);
                user.setEmailVerified(true);
                updated = true;
                log.info("Updated existing user with OAuth info: {}", email);
            }

            // CRITICAL: Ensure OAuth users always have USER role
            if (user.getRole() != Role.USER) {
                user.setRole(Role.USER);
                updated = true;
                log.info("Fixed role to USER for OAuth user: {}", email);
            }

            if (updated) {
                user.setUpdatedAt(LocalDateTime.now());
                user = userRepository.save(user);
            }

            return user;
        } else {
            // Create new user with USER role and random password
            String username = generateUniqueUsername(firstName, lastName, email);

            User newUser = User.builder()
                    .username(username)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .fullName(fullName != null ? fullName : (firstName + " " + lastName))
                    .provider(provider)
                    .providerId(providerId)
                    .avatarUrl(avatarUrl)
                    .isOAuthUser(true)
                    .emailVerified(true)  // Google emails are verified
                    .isActive(true)
                    .role(Role.USER)  // ALWAYS USER ROLE FOR GOOGLE LOGIN
                    .password(UUID.randomUUID().toString())  // ← FIX: Random password for OAuth users
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            newUser = userRepository.save(newUser);
            log.info("Created new OAuth user: {} with role: {}", email, newUser.getRole());
            return newUser;
        }
    }

    private String generateUniqueUsername(String firstName, String lastName, String email) {
        String baseUsername = (firstName != null ? firstName : "") + (lastName != null ? lastName : "");
        baseUsername = baseUsername.toLowerCase().replaceAll("\\s", "");

        if (baseUsername.isEmpty()) {
            baseUsername = email.split("@")[0];
        }

        String username = baseUsername;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }
}