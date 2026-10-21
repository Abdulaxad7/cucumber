package com.example.authservice.exception;

public class InvitationNotPendingException extends ApiException {

    public InvitationNotPendingException() {
        super(ErrorCode.INVITATION_NOT_PENDING, "This invitation is no longer pending.");
    }
}
