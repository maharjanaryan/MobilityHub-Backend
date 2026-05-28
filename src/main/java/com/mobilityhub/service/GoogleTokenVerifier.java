// service/GoogleTokenVerifier.java
package com.mobilityhub.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mobilityhub.dto.OAuth2UserInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
public class GoogleTokenVerifier {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    public OAuth2UserInfoDto verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                String userId = payload.getSubject();
                String email = payload.getEmail();
                String firstName = (String) payload.get("given_name");
                String lastName = (String) payload.get("family_name");
                String fullName = (String) payload.get("name");
                String avatarUrl = (String) payload.get("picture");
                boolean emailVerified = payload.getEmailVerified();

                return OAuth2UserInfoDto.builder()
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .fullName(fullName)
                        .providerId(userId)
                        .provider("GOOGLE")
                        .avatarUrl(avatarUrl)
                        .build();
            } else {
                throw new RuntimeException("Invalid ID token");
            }
        } catch (Exception e) {
            log.error("Error verifying Google token: {}", e.getMessage());
            throw new RuntimeException("Failed to verify Google token", e);
        }
    }
}