package com.example.authservice.service;

import com.example.authservice.model.Invitation;
import com.example.authservice.model.User;

import java.util.UUID;

public interface InvitationService {
    Invitation findByToken(String token);
    void acceptInvitation(String token, User user);
}
