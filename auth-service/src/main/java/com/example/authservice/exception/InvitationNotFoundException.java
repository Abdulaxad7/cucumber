package com.example.authservice.exception;

public class InvitationNotFoundException extends ApiException {

    public InvitationNotFoundException() {
        super(ErrorCode.INVITATION_NOT_FOUND, "No invitation matches this request.");
    }
}
