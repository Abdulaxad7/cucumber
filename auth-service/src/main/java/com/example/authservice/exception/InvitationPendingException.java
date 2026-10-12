package com.example.authservice.exception;

/** 409 {@code INVITATION_PENDING}: a pending, unexpired invitation for this email already exists. */
public class InvitationPendingException extends ApiException {

    public InvitationPendingException() {
        super(ErrorCode.INVITATION_PENDING, "A pending invitation for this email already exists.");
    }
}
