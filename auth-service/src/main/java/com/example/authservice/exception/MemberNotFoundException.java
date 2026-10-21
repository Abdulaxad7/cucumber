package com.example.authservice.exception;

public class MemberNotFoundException extends ApiException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND, "This user isn't a member of the organization.");
    }
}
