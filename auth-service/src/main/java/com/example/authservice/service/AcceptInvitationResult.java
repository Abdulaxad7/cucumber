package com.example.authservice.service;

import com.example.authservice.dto.response.AcceptInvitationResponse;

public record AcceptInvitationResult(
        AcceptInvitationResponse response,
        boolean accountCreated
) {
}
