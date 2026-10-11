package com.example.authservice.dto.response;

import com.example.authservice.enums.UserStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        boolean mfaEnabled,
        LocalDateTime createdAt
) {
}
