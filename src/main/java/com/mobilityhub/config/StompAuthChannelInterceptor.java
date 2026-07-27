// config/StompAuthChannelInterceptor.java
package com.mobilityhub.config;

import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (jwtUtils.validateJwtToken(token)) {
                    String username = jwtUtils.getUserNameFromJwtToken(token);

                    User user = userRepository.findByUsername(username).orElse(null);

                    if (user != null) {
                        // Must match the string form used in
                        // ChatService: receiver.getId().toString()
                        String userId = user.getId().toString();
                        Principal principal = () -> userId;
                        accessor.setUser(principal);
                        log.info("STOMP CONNECT authenticated: username={}, userId={}", username, userId);
                    } else {
                        log.warn("STOMP CONNECT: no user found for username={}", username);
                    }
                } else {
                    log.warn("STOMP CONNECT: invalid JWT token");
                }
            } else {
                log.warn("STOMP CONNECT: missing Authorization header");
            }
        }

        return message;
    }
}