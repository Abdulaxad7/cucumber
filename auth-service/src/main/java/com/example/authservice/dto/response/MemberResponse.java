package com.example.authservice.dto.response;

import com.example.authservice.enums.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String email,
        String fullName,
        OrganizationRole role,
        Instant joinedAt
) {
}
