package com.connectsphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendEmailRequest(
        @NotNull(message = "Recipient id is required")
        Long recipientId,
        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must be up to 200 characters")
        String subject,
        @NotBlank(message = "Body is required")
        @Size(max = 5000, message = "Body must be up to 5000 characters")
        String body
) {
}
