package com.example.authservice.exception;

/** 404 {@code MEMBER_NOT_FOUND}: the target user isn't a member of this organization (or doesn't exist). */
public class MemberNotFoundException extends ApiException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND, "This user isn't a member of the organization.");
    }
}
