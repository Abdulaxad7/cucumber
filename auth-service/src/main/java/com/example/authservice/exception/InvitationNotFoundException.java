package com.example.authservice.exception;

/** 404 {@code INVITATION_NOT_FOUND}: no invitation has this token, or this id in this organization. */
public class InvitationNotFoundException extends ApiException {

    public InvitationNotFoundException() {
        super(ErrorCode.INVITATION_NOT_FOUND, "No invitation matches this request.");
    }
}
