package com.example.authservice.service;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.response.AcceptInvitationResponse;

import java.util.UUID;

public interface InvitationService {

    /** A logged-in account accepts an invitation addressed to its email. */
    AcceptInvitationResponse acceptInvitation(String token, UUID userId);

    /** An anonymous caller creates an account with the invitation's email and joins, in one transaction. */
    AcceptInvitationResponse acceptInvitationAsNewUser(String token, AcceptInvitationRequest newAccount);
}
