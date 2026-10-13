package com.example.authservice.exception;

public class ProjectMemberNotFoundException extends ApiException {

    public ProjectMemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND, "This user isn't a member of the project.");
    }
}
