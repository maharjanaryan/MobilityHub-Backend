// security/oauth2/OAuth2AuthenticationSuccessHandler.java
package com.mobilityhub.security.oauth2;

import com.mobilityhub.security.jwt.JwtUtils;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // Get OAuth2User (not UserDetailsImpl for OAuth)
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Extract email from OAuth2User attributes
        String email = oAuth2User.getAttribute("email");
        log.info("OAuth2 login successful for email: {}", email);

        // Find the user in database
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.error("User not found in database after OAuth: {}", email);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/signin?error=User not found");
            return;
        }

        User user = userOpt.get();

        // Generate JWT token (you'll need to create a method to generate token from User object)
        String jwt = jwtUtils.generateTokenFromUsername(user.getUsername(), 86400000);
        String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());

        // Role is always USER for Google OAuth
        String role = "USER";

        // Build redirect URL with tokens
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("accessToken", jwt)
                .queryParam("refreshToken", refreshToken)
                .queryParam("tokenType", "Bearer")
                .queryParam("userId", user.getId())
                .queryParam("username", user.getUsername())
                .queryParam("email", user.getEmail())
                .queryParam("fullName", user.getFullName())
                .queryParam("role", role)
                .build()
                .toUriString();

        log.info("Redirecting to: {}", targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}