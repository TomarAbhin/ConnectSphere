package com.connectsphere.auth.serviceimpl;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.ChangePasswordRequest;
import com.connectsphere.auth.dto.LoginRequest;
import com.connectsphere.auth.dto.RegisterRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenStore tokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
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
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(true);

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = getActiveUserByEmail(email);

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
    public void deactivateAccount(String email) {
        User user = getActiveUserByEmail(email);
        user.setActive(false);
        userRepository.save(user);
    }

    private AuthTokenResponse issueTokens(User user) {
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
                user.getCreatedAt()
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
}
