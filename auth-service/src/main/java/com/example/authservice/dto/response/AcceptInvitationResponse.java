package com.example.authservice.dto.response;

public record AcceptInvitationResponse(
        UserResponse user,
        OrganizationResponse organization
) {
}
