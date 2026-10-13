package com.example.authservice.exception;

public class InvitationPendingException extends ApiException {

    public InvitationPendingException() {
        super(ErrorCode.INVITATION_PENDING, "A pending invitation for this email already exists.");
    }
}
