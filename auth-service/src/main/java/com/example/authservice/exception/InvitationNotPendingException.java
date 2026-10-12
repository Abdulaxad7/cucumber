package com.example.authservice.exception;

/** 409 {@code INVITATION_NOT_PENDING}: only a pending invitation can be revoked. */
public class InvitationNotPendingException extends ApiException {

    public InvitationNotPendingException() {
        super(ErrorCode.INVITATION_NOT_PENDING, "This invitation is no longer pending.");
    }
}
