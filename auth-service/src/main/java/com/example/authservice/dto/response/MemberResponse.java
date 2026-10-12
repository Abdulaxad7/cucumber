package com.example.authservice.dto.response;

import com.example.authservice.enums.OrganizationRole;

import java.time.LocalDateTime;
import java.util.UUID;

/** A member of an organization. Never contains a password or its hash (P3). */
public record MemberResponse(
        UUID userId,
        String email,
        String fullName,
        OrganizationRole role,
        LocalDateTime joinedAt
) {
}
