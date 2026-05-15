package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.ChangePasswordRequest;
import com.connectsphere.auth.dto.LoginRequest;
import com.connectsphere.auth.dto.RegisterRequest;
import com.connectsphere.auth.dto.UpdateProfileRequest;
import com.connectsphere.auth.dto.PlatformAnalyticsResponse;
import com.connectsphere.auth.dto.UserResponse;
import com.connectsphere.auth.dto.UserSearchResponse;
import com.connectsphere.auth.entity.UserRole;
import java.util.List;

public interface AuthService {

    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    void logout(String token);

    boolean validateToken(String token);

    AuthTokenResponse refreshToken(String refreshToken);

    UserResponse getUserByEmail(String email);

    UserResponse getUserById(Long userId);

    UserResponse updateProfile(String email, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);

    List<UserSearchResponse> searchUsers(String query, UserRole role);

    List<UserResponse> searchAllUsers(String authorizationHeader, String query, UserRole role);

    void suspendUserById(String authorizationHeader, Long userId);

    void reactivateUserById(String authorizationHeader, Long userId);

    void deactivateAccount(String authorizationHeader, String email);

    void deactivateUserById(String authorizationHeader, Long userId);

    PlatformAnalyticsResponse getPlatformAnalytics(String authorizationHeader);
}
