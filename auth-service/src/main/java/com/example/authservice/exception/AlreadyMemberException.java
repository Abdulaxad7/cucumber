package com.example.authservice.exception;

/** 409 {@code ALREADY_MEMBER}: the invitee or the accepting account is already a member of the organization. */
public class AlreadyMemberException extends ApiException {

    public AlreadyMemberException() {
        super(ErrorCode.ALREADY_MEMBER, "This person is already a member of the organization.");
    }
}
