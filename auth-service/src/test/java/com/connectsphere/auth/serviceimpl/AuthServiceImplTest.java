package com.connectsphere.auth.serviceimpl;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.RegisterRequest;
import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.entity.UserRole;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtService;
import com.connectsphere.auth.security.TokenStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenStore tokenStore;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtService,
                tokenStore,
                "http://post-service",
                "http://search-service",
                "admin-key"
        );
    }

    @Test
    void registerCreatesUserAndIssuesTokens() {
        Instant refreshExpiration = Instant.parse("2026-05-05T12:00:00Z");
        RegisterRequest request = new RegisterRequest(
                "DemoUser",
                "demo@example.com",
                "Password123!",
                "Demo User",
                UserRole.USER,
                null
        );

        when(userRepository.existsByEmailIgnoreCase("demo@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("demouser")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtService.extractExpiration("refresh-token")).thenReturn(refreshExpiration);
        when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);

        AuthTokenResponse response = authService.register(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresInSeconds());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("demouser", savedUser.getUsername());
        assertEquals("demo@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPasswordHash());
        assertEquals(UserRole.USER, savedUser.getRole());
        assertEquals(AuthProvider.LOCAL, savedUser.getProvider());
        assertTrue(savedUser.isActive());
        assertNotNull(savedUser.getLastLoginAt());
        verify(tokenStore).storeRefreshToken(eq("demo@example.com"), eq("refresh-token"), eq(refreshExpiration));
    }
}