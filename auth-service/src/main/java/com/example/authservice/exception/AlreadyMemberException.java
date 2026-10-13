package com.example.authservice.exception;

public class AlreadyMemberException extends ApiException {

    public AlreadyMemberException() {
        super(ErrorCode.ALREADY_MEMBER, "This person is already a member of the organization.");
    }
}
