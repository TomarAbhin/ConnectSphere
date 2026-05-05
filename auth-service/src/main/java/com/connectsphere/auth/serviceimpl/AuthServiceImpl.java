package com.connectsphere.auth.serviceimpl;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.ChangePasswordRequest;
import com.connectsphere.auth.dto.LoginRequest;
import com.connectsphere.auth.dto.PlatformAnalyticsResponse;
import com.connectsphere.auth.dto.RegisterRequest;
import com.connectsphere.auth.dto.TrendingHashtagResponse;
import com.connectsphere.auth.dto.UpdateProfileRequest;
import com.connectsphere.auth.dto.UserResponse;
import com.connectsphere.auth.dto.UserSearchResponse;
import com.connectsphere.auth.entity.AuthProvider;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.entity.UserRole;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtService;
import com.connectsphere.auth.security.TokenStore;
import com.connectsphere.auth.service.AuthService;
import java.util.List;
import java.time.Instant;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final String postServiceUrl;
    private final String searchServiceUrl;
    private final String adminRegistrationKey;
    private final String notificationServiceUrl;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenStore tokenStore,
                @Value("${app.services.post.url:http://localhost:8082}") String postServiceUrl,
                @Value("${app.services.search.url:http://localhost:8088}") String searchServiceUrl,
                @Value("${app.security.admin-registration-key}") String adminRegistrationKey,
                @Value("${app.services.notification-service.url:http://localhost:8086}") String notificationServiceUrl
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.postServiceUrl = postServiceUrl;
        this.searchServiceUrl = searchServiceUrl;
        this.adminRegistrationKey = adminRegistrationKey == null ? "" : adminRegistrationKey.trim();
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Override
    public AuthTokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(trimToNull(request.fullName()));
        UserRole requestedRole = request.role() == null ? UserRole.USER : request.role();
        if (requestedRole == UserRole.ADMIN) {
            validateAdminRegistrationKey(request.adminSecretKey());
        }
        user.setRole(requestedRole);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(true);

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        String identifier = request.emailOrUsername().trim().toLowerCase();
        User user = identifier.contains("@")
                ? userRepository.findByEmail(identifier).orElseThrow(this::invalidCredentials)
                : userRepository.findByUsername(identifier).orElseThrow(this::invalidCredentials);

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return issueTokens(user);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            tokenStore.blacklist(token, jwtService.extractExpiration(token));
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (token == null || token.isBlank() || tokenStore.isBlacklisted(token)) {
            return false;
        }
        try {
            String email = jwtService.extractUsername(token);
            User user = userRepository.findByEmail(email).orElse(null);
            return user != null
                    && user.isActive()
                    && !jwtService.isRefreshToken(token)
                    && jwtService.isTokenValid(token, email);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public AuthTokenResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }

            String email = jwtService.extractUsername(refreshToken);
            boolean tokenValid = jwtService.isTokenValid(refreshToken, email)
                    && tokenStore.isValidRefreshToken(email, refreshToken);
            if (!tokenValid) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
            }

            User user = getActiveUserByEmail(email);
            tokenStore.revokeRefreshToken(refreshToken);
            return issueTokens(user);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        return toUserResponse(getActiveUserByEmail(email));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return toUserResponse(userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user not found")));
    }

    @Override
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = getActiveUserByEmail(email);
        ensureWritableAccount(user);

        if (request.fullName() != null) {
            user.setFullName(trimToNull(request.fullName()));
        }
        if (request.bio() != null) {
            user.setBio(trimToNull(request.bio()));
        }
        if (request.profilePicUrl() != null) {
            user.setProfilePicUrl(trimToNull(request.profilePicUrl()));
        }

        return toUserResponse(userRepository.save(user));
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getActiveUserByEmail(email);
        ensureWritableAccount(user);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String query, UserRole role) {
        String safeQuery = query == null ? "" : query.trim();
        return userRepository.searchActiveUsers(safeQuery, role)
                .stream()
                .map(this::toUserSearchResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> searchAllUsers(String authorizationHeader, String query, UserRole role) {
        ensureAdmin(authorizationHeader);
        String safeQuery = query == null ? "" : query.trim();
        return userRepository.searchAllUsers(safeQuery, role)
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Override
    public void suspendUserById(String authorizationHeader, Long userId) {
        ensureAdmin(authorizationHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isActive()) {
            return;
        }
        user.setActive(false);
        userRepository.save(user);
        tokenStore.revokeRefreshTokensForEmail(user.getEmail());
        // notify user by email via notification service
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("recipientId", user.getUserId());
            body.put("subject", "Account suspended");
            body.put("body", "Your account has been suspended by an administrator.");
            body.put("deepLink", "/profile/" + user.getUserId());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authorizationHeader != null) headers.set("Authorization", authorizationHeader);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> req = new org.springframework.http.HttpEntity<>(body, headers);
            new org.springframework.web.client.RestTemplate().postForObject(notificationServiceUrl + "/notifications/email", req, java.util.Map.class);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void reactivateUserById(String authorizationHeader, Long userId) {
        ensureAdmin(authorizationHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isActive()) {
            return;
        }
        user.setActive(true);
        userRepository.save(user);
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("recipientId", user.getUserId());
            body.put("subject", "Account reactivated");
            body.put("body", "Your account has been reactivated by an administrator.");
            body.put("deepLink", "/profile/" + user.getUserId());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authorizationHeader != null) headers.set("Authorization", authorizationHeader);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> req = new org.springframework.http.HttpEntity<>(body, headers);
            new org.springframework.web.client.RestTemplate().postForObject(notificationServiceUrl + "/notifications/email", req, java.util.Map.class);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deactivateAccount(String authorizationHeader, String email) {
        User user = getActiveUserByEmail(email);
        removeUserContent(user.getUserId(), authorizationHeader);
        tokenStore.revokeRefreshTokensForEmail(user.getEmail());
        userRepository.delete(user);
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("recipientId", user.getUserId());
            body.put("subject", "Account removed");
            body.put("body", "Your account has been removed.");
            body.put("deepLink", "/");
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authorizationHeader != null) headers.set("Authorization", authorizationHeader);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> req = new org.springframework.http.HttpEntity<>(body, headers);
            new org.springframework.web.client.RestTemplate().postForObject(notificationServiceUrl + "/notifications/email", req, java.util.Map.class);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deactivateUserById(String authorizationHeader, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        ensureAdmin(authorizationHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        removeUserContent(userId, authorizationHeader);
        tokenStore.revokeRefreshTokensForEmail(user.getEmail());
        userRepository.delete(user);
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("recipientId", user.getUserId());
            body.put("subject", "Account deleted");
            body.put("body", "Your account has been deleted by an administrator.");
            body.put("deepLink", "/");
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authorizationHeader != null) headers.set("Authorization", authorizationHeader);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> req = new org.springframework.http.HttpEntity<>(body, headers);
            new org.springframework.web.client.RestTemplate().postForObject(notificationServiceUrl + "/notifications/email", req, java.util.Map.class);
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformAnalyticsResponse getPlatformAnalytics(String authorizationHeader) {
        ensureAdmin(authorizationHeader);
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        long suspendedUsers = userRepository.countByActiveFalse();
        long dailyActiveUsers = userRepository.countByLastLoginAtAfter(Instant.now().minus(Duration.ofHours(24)));
        long totalPosts = fetchPostCount();
        List<TrendingHashtagResponse> trendingHashtags = fetchTrendingHashtags();
        return new PlatformAnalyticsResponse(
                totalUsers,
                activeUsers,
                suspendedUsers,
                dailyActiveUsers,
                totalPosts,
                trendingHashtags,
                Instant.now()
        );
    }

    private AuthTokenResponse issueTokens(User user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        tokenStore.storeRefreshToken(user.getEmail(), refreshToken, jwtService.extractExpiration(refreshToken));
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessExpirationSeconds());
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getBio(),
                user.getProfilePicUrl(),
                user.getRole(),
                user.getProvider(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    private UserSearchResponse toUserSearchResponse(User user) {
        return new UserSearchResponse(
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getProfilePicUrl(),
                user.getRole()
        );
    }

    private User getActiveUserByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user not found"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private void validateAdminRegistrationKey(String providedSecret) {
        String normalizedSecret = trimToNull(providedSecret);
        if (adminRegistrationKey.isBlank() || normalizedSecret == null || !adminRegistrationKey.equals(normalizedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin registration key");
        }
    }

    private void ensureAdmin(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        try {
            if (token == null || !jwtService.isTokenValid(token, jwtService.extractUsername(token))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        User user = userRepository.findByEmail(jwtService.extractUsername(token))
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }

    private void ensureWritableAccount(User user) {
        if (user != null && user.getRole() == UserRole.GUEST) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest accounts have read-only access");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }

    private void removeUserContent(Long userId, String authorizationHeader) {
        if (userId == null) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                headers.set("Authorization", authorizationHeader);
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);
            new RestTemplate().exchange(postServiceUrl + "/posts/admin/author/" + userId, org.springframework.http.HttpMethod.DELETE, request, Void.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to remove user posts");
        }
    }

    private long fetchPostCount() {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = new RestTemplate().getForObject(postServiceUrl + "/posts/count", java.util.Map.class);
            if (response == null) {
                return 0L;
            }
            Object count = response.get("count");
            if (count instanceof Number number) {
                return number.longValue();
            }
            Object total = response.get("total");
            if (total instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private List<TrendingHashtagResponse> fetchTrendingHashtags() {
        try {
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> response = new RestTemplate().getForObject(searchServiceUrl + "/search/hashtags/trending", List.class);
            if (response == null) {
                return List.of();
            }
            return response.stream().map(item -> new TrendingHashtagResponse(
                    toLong(item.get("hashtagId")),
                    item.get("tag") == null ? null : String.valueOf(item.get("tag")),
                    toLong(item.get("postCount")),
                    item.get("lastUsedAt") == null ? null : Instant.parse(String.valueOf(item.get("lastUsedAt"))),
                    item.get("createdAt") == null ? null : Instant.parse(String.valueOf(item.get("createdAt")))
            )).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
