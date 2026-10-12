package com.example.authservice.dto.response;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The create-invitation response: the only response that contains the acceptance token, returned once to the
 * inviter (D11, P3).
 */
public record CreatedInvitationResponse(
        UUID id,
        String email,
        OrganizationRole role,
        InvitationStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String token
) {

    @Override
    public String toString() {
        return "CreatedInvitationResponse[id=" + id + ", role=" + role + ", status=" + status + ", token=<redacted>]";
    }
}
