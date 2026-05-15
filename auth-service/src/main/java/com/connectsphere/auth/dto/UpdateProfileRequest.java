package com.connectsphere.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 120, message = "Full name must be up to 120 characters")
        String fullName,

        @Size(max = 600, message = "Bio must be up to 600 characters")
        String bio,

        @Size(max = 500, message = "Profile picture URL must be up to 500 characters")
        String profilePicUrl
) {
}
