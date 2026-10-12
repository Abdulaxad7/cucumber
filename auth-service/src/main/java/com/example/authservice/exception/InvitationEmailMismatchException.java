package com.example.authservice.exception;

/** 403 {@code INVITATION_EMAIL_MISMATCH}: the logged-in account's email differs from the invitation's. */
public class InvitationEmailMismatchException extends ApiException {

    public InvitationEmailMismatchException() {
        super(ErrorCode.INVITATION_EMAIL_MISMATCH, "This invitation was sent to another email address.");
    }
}
