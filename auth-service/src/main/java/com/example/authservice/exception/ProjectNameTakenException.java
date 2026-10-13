package com.example.authservice.exception;

public class ProjectNameTakenException extends ApiException {

    public ProjectNameTakenException() {
        super(ErrorCode.PROJECT_NAME_TAKEN, "Another project in this organization already has this name.");
    }
}
