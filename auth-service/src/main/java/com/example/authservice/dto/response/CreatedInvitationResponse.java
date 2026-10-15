package com.example.authservice.dto.response;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

public record CreatedInvitationResponse(
        UUID id,
        String email,
        OrganizationRole role,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt,
        String token
) {

    @Override
    public String toString() {
        return "CreatedInvitationResponse[id=" + id + ", role=" + role + ", status=" + status + ", token=<redacted>]";
    }
}
