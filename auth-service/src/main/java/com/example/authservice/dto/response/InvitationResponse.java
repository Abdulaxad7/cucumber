package com.example.authservice.dto.response;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;

import java.time.LocalDateTime;
import java.util.UUID;

/** A pending invitation as listed. Never contains the acceptance token (P3). */
public record InvitationResponse(
        UUID id,
        String email,
        OrganizationRole role,
        InvitationStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
