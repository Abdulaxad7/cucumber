package com.example.authservice.dto.response;

/**
 * The accept-invitation response, the same for an existing account (200) and a new one (201) (Q2): the account and
 * the organization it joined, with its role there.
 */
public record AcceptInvitationResponse(
        UserResponse user,
        OrganizationResponse organization
) {
}
