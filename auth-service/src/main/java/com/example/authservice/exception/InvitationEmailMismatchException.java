package com.example.authservice.exception;

public class InvitationEmailMismatchException extends ApiException {

    public InvitationEmailMismatchException() {
        super(ErrorCode.INVITATION_EMAIL_MISMATCH, "This invitation was sent to another email address.");
    }
}
