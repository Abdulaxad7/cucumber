package com.example.authservice.service;

import com.example.authservice.dto.response.AcceptInvitationResponse;
import lombok.Builder;

@Builder
public record AcceptInvitationResult(
        AcceptInvitationResponse response,
        boolean accountCreated
) {
}
