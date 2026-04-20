package com.connectsphere.auth.resource;

import com.connectsphere.auth.dto.AuthTokenResponse;
import com.connectsphere.auth.dto.ChangePasswordRequest;
import com.connectsphere.auth.dto.LoginRequest;
import com.connectsphere.auth.dto.MessageResponse;
import com.connectsphere.auth.dto.RefreshTokenRequest;
import com.connectsphere.auth.dto.RegisterRequest;
import com.connectsphere.auth.dto.UpdateProfileRequest;
import com.connectsphere.auth.dto.UserResponse;
import com.connectsphere.auth.dto.UserSearchResponse;
import com.connectsphere.auth.entity.UserRole;
import com.connectsphere.auth.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthResource {

    private final AuthService authService;

    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        authService.logout(extractBearerToken(authorization));
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.refreshToken()));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Boolean>> validateToken(
            @RequestParam(name = "token", required = false) String token,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String tokenToValidate = token != null ? token : extractBearerToken(authorization);
        return ResponseEntity.ok(Map.of("valid", authService.validateToken(tokenToValidate)));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> profile(Principal principal) {
        return ResponseEntity.ok(authService.getUserByEmail(requiredPrincipalName(principal)));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(authService.updateProfile(requiredPrincipalName(principal), request));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(requiredPrincipalName(principal), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam(name = "query", defaultValue = "") String query,
            @RequestParam(name = "role", required = false) UserRole role
    ) {
        return ResponseEntity.ok(authService.searchUsers(query, role));
    }

    @PutMapping("/deactivate")
    public ResponseEntity<MessageResponse> deactivate(
            Principal principal,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        authService.deactivateAccount(requiredPrincipalName(principal));
        authService.logout(extractBearerToken(authorization));
        return ResponseEntity.ok(new MessageResponse("Account deactivated successfully"));
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private String requiredPrincipalName(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal.getName();
    }
}
