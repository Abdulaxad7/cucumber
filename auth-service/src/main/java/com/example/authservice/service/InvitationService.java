package com.example.authservice.service;

import com.example.authservice.dto.request.AcceptInvitationRequest;

import java.util.UUID;

public interface InvitationService {

    AcceptInvitationResult accept(String token, UUID callerId, AcceptInvitationRequest newAccount);
}
