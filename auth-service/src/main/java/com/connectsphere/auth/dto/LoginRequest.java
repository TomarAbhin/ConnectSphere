package com.connectsphere.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email or username is required")
        String emailOrUsername,

        @NotBlank(message = "Password is required")
        String password
) {
}
