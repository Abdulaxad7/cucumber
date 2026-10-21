package com.example.authservice.dto.response;

import com.example.authservice.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        boolean mfaEnabled,
        Instant createdAt
) {
}
