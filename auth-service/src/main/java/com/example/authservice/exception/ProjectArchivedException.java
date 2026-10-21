package com.example.authservice.exception;

public class ProjectArchivedException extends ApiException {

    public ProjectArchivedException() {
        super(ErrorCode.PROJECT_ARCHIVED, "The project is archived. Restore it first.");
    }
}
