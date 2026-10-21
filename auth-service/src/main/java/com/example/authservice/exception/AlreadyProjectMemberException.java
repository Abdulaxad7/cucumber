package com.example.authservice.exception;

public class AlreadyProjectMemberException extends ApiException {

    public AlreadyProjectMemberException() {
        super(ErrorCode.ALREADY_MEMBER, "This person is already a member of the project.");
    }
}
