package com.connectsphere.auth.serviceimpl;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.ChangePasswordRequest;
import com.connectsphere.auth.dto.LoginRequest;
import com.connectsphere.auth.dto.RegisterRequest;
import com.connectsphere.auth.dto.UpdateProfileRequest;
import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.entity.UserRole;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtService;
import com.connectsphere.auth.security.TokenStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void registerRejectsDuplicateEmailAndBadAdminSecret() {
        when(userRepository.existsByEmailIgnoreCase("demo@example.com")).thenReturn(true);

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class, () -> authService.register(
                new RegisterRequest("demo", "demo@example.com", "Password123!", "Demo", UserRole.USER, null)
        ));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(false);
        ResponseStatusException badSecret = assertThrows(ResponseStatusException.class, () -> authService.register(
                new RegisterRequest("admin", "admin@example.com", "Password123!", "Admin", UserRole.ADMIN, "wrong")
        ));
        assertEquals(HttpStatus.FORBIDDEN, badSecret.getStatusCode());
    }

    @Test
    void registerRejectsDuplicateUsernameAndAcceptsValidAdminSecret() {
        when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        ResponseStatusException duplicateUsername = assertThrows(ResponseStatusException.class, () -> authService.register(
                new RegisterRequest("admin", "admin@example.com", "Password123!", "Admin", UserRole.ADMIN, "admin-key")
        ));
        assertEquals(HttpStatus.CONFLICT, duplicateUsername.getStatusCode());

        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("owner")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtService.extractExpiration("refresh-token")).thenReturn(Instant.parse("2026-05-05T12:00:00Z"));
        when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);

        AuthTokenResponse response = authService.register(
                new RegisterRequest("owner", "owner@example.com", "Password123!", "Owner", UserRole.ADMIN, " admin-key ")
        );

        assertEquals("access-token", response.accessToken());
    }

    @Test
    void loginUsesEmailOrUsernameAndRejectsBadPassword() {
        User user = user(1L, "demo", "demo@example.com", UserRole.USER, true);
        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        stubIssuedTokens(user);

        AuthTokenResponse response = authService.login(new LoginRequest(" Demo@Example.com ", "Password123!"));

        assertEquals("access-token", response.accessToken());

        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> authService.login(new LoginRequest("demo", "bad")));
    }

    @Test
    void loginRejectsInactiveAndMissingUsers() {
        User inactive = user(1L, "demo", "demo@example.com", UserRole.USER, false);
        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(inactive));

        ResponseStatusException inactiveError = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo@example.com", "Password123!"))
        );

        assertEquals(HttpStatus.FORBIDDEN, inactiveError.getStatusCode());

        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        ResponseStatusException missingError = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("missing", "Password123!"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, missingError.getStatusCode());
    }

    @Test
    void validateRefreshAndLogoutTokenLifecycle() {
        User user = user(1L, "demo", "demo@example.com", UserRole.USER, true);
        when(tokenStore.isBlacklisted("access")).thenReturn(false);
        when(jwtService.extractUsername("access")).thenReturn("demo@example.com");
        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isRefreshToken("access")).thenReturn(false);
        when(jwtService.isTokenValid("access", "demo@example.com")).thenReturn(true);

        assertTrue(authService.validateToken("access"));
        assertFalse(authService.validateToken(""));

        when(jwtService.extractExpiration("access")).thenReturn(Instant.parse("2026-05-05T12:00:00Z"));
        authService.logout("access");
        verify(tokenStore).blacklist("access", Instant.parse("2026-05-05T12:00:00Z"));

        when(jwtService.isRefreshToken("refresh")).thenReturn(true);
        when(jwtService.extractUsername("refresh")).thenReturn("demo@example.com");
        when(jwtService.isTokenValid("refresh", "demo@example.com")).thenReturn(true);
        when(tokenStore.isValidRefreshToken("demo@example.com", "refresh")).thenReturn(true);
        stubIssuedTokens(user);

        AuthTokenResponse refreshed = authService.refreshToken("refresh");

        assertEquals("access-token", refreshed.accessToken());
        verify(tokenStore).revokeRefreshToken("refresh");
    }

    @Test
    void tokenValidationAndRefreshRejectInvalidTokens() {
        when(tokenStore.isBlacklisted("blocked")).thenReturn(true);
        assertFalse(authService.validateToken("blocked"));

        when(tokenStore.isBlacklisted("broken")).thenReturn(false);
        when(jwtService.extractUsername("broken")).thenThrow(new IllegalArgumentException("bad token"));
        assertFalse(authService.validateToken("broken"));

        ResponseStatusException blankRefresh = assertThrows(
                ResponseStatusException.class,
                () -> authService.refreshToken(" ")
        );
        assertEquals(HttpStatus.BAD_REQUEST, blankRefresh.getStatusCode());

        when(jwtService.isRefreshToken("access-token")).thenReturn(false);
        ResponseStatusException accessToken = assertThrows(
                ResponseStatusException.class,
                () -> authService.refreshToken("access-token")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, accessToken.getStatusCode());

        when(jwtService.isRefreshToken("expired-refresh")).thenReturn(true);
        when(jwtService.extractUsername("expired-refresh")).thenReturn("demo@example.com");
        when(jwtService.isTokenValid("expired-refresh", "demo@example.com")).thenReturn(true);
        when(tokenStore.isValidRefreshToken("demo@example.com", "expired-refresh")).thenReturn(false);
        ResponseStatusException expiredRefresh = assertThrows(
                ResponseStatusException.class,
                () -> authService.refreshToken("expired-refresh")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, expiredRefresh.getStatusCode());
    }

    @Test
    void profilePasswordAndSearchOperationsMapUsers() {
        User user = user(1L, "demo", "demo@example.com", UserRole.USER, true);
        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.updateProfile(" Demo@Example.com ", new UpdateProfileRequest(" Demo User ", " Bio ", " pic "));
        assertEquals("Demo User", response.fullName());
        assertEquals("Bio", response.bio());

        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("newPassword", "hash")).thenReturn(false);
        when(passwordEncoder.encode("newPassword")).thenReturn("new-hash");
        authService.changePassword("demo@example.com", new ChangePasswordRequest("old", "newPassword"));
        assertEquals("new-hash", user.getPasswordHash());

        when(userRepository.searchActiveUsers("de", UserRole.USER)).thenReturn(List.of(user));
        assertEquals("demo", authService.searchUsers(" de ", UserRole.USER).get(0).username());
    }

    @Test
    void readOperationsHandleMissingAndGuestAccounts() {
        User guest = user(1L, "guest", "guest@example.com", UserRole.GUEST, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(guest));
        assertEquals(UserRole.GUEST, authService.getUserById(1L).role());

        when(userRepository.findByEmail("guest@example.com")).thenReturn(Optional.of(guest));
        ResponseStatusException profileUpdate = assertThrows(
                ResponseStatusException.class,
                () -> authService.updateProfile("guest@example.com", new UpdateProfileRequest("Guest", null, null))
        );
        assertEquals(HttpStatus.FORBIDDEN, profileUpdate.getStatusCode());

        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> authService.getUserById(404L));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void adminCanSearchSuspendReactivateAndDeactivateUsers() {
        User admin = user(1L, "admin", "admin@example.com", UserRole.ADMIN, true);
        User target = user(2L, "target", "target@example.com", UserRole.USER, true);
        stubAdmin(admin);
        when(userRepository.searchAllUsers("target", UserRole.USER)).thenReturn(List.of(target));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("target", authService.searchAllUsers("Bearer admin-token", "target", UserRole.USER).get(0).username());
        authService.suspendUserById("Bearer admin-token", 2L);
        assertFalse(target.isActive());
        verify(tokenStore).revokeRefreshTokensForEmail("target@example.com");

        target.setActive(false);
        authService.reactivateUserById("Bearer admin-token", 2L);
        assertTrue(target.isActive());

    }

    @Test
    void adminActionsRejectNonAdminsAdminTargetsAndMissingIds() {
        User nonAdmin = user(1L, "mod", "mod@example.com", UserRole.USER, true);
        stubAdmin(nonAdmin);

        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> authService.searchAllUsers("Bearer admin-token", "target", UserRole.USER)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        User admin = user(2L, "admin", "admin@example.com", UserRole.ADMIN, true);
        User targetAdmin = user(3L, "targetadmin", "targetadmin@example.com", UserRole.ADMIN, true);
        stubAdmin(admin);
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetAdmin));

        ResponseStatusException adminTarget = assertThrows(
                ResponseStatusException.class,
                () -> authService.suspendUserById("Bearer admin-token", 3L)
        );
        assertEquals(HttpStatus.FORBIDDEN, adminTarget.getStatusCode());

        ResponseStatusException missingId = assertThrows(
                ResponseStatusException.class,
                () -> authService.deactivateUserById("Bearer admin-token", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, missingId.getStatusCode());
    }

    @Test
    void deactivateAccountReportsGatewayFailureWhenContentRemovalFails() {
        AuthServiceImpl serviceWithClosedPostPort = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtService,
                tokenStore,
                "http://127.0.0.1:1",
                "http://search-service",
                "admin-key"
        );
        User user = user(1L, "demo", "demo@example.com", UserRole.USER, true);
        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> serviceWithClosedPostPort.deactivateAccount("Bearer token", "demo@example.com")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void adminAnalyticsUsesRepositoryCountsWhenRemoteServicesFallback() {
        User admin = user(1L, "admin", "admin@example.com", UserRole.ADMIN, true);
        stubAdmin(admin);
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByActiveTrue()).thenReturn(7L);
        when(userRepository.countByActiveFalse()).thenReturn(3L);
        when(userRepository.countByLastLoginAtAfter(any(Instant.class))).thenReturn(4L);

        var analytics = authService.getPlatformAnalytics("Bearer admin-token");

        assertEquals(10L, analytics.totalUsers());
        assertEquals(7L, analytics.activeUsers());
        assertEquals(0L, analytics.totalPosts());
        assertTrue(analytics.trendingHashtags().isEmpty());
    }

    private void stubIssuedTokens(User user) {
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.extractExpiration("refresh-token")).thenReturn(Instant.parse("2026-05-05T12:00:00Z"));
        when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);
        when(userRepository.save(user)).thenReturn(user);
    }

    private void stubAdmin(User admin) {
        when(jwtService.extractUsername("admin-token")).thenReturn(admin.getEmail());
        when(jwtService.isTokenValid("admin-token", admin.getEmail())).thenReturn(true);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
    }

    private User user(Long id, String username, String email, UserRole role, boolean active) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName("Full " + username);
        user.setRole(role);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(active);
        user.setCreatedAt(Instant.parse("2026-05-05T00:00:00Z"));
        return user;
    }
}
