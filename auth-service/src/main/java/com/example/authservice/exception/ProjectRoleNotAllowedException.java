package com.example.authservice.exception;

public class ProjectRoleNotAllowedException extends ApiException {

    public ProjectRoleNotAllowedException() {
        super(ErrorCode.PROJECT_ROLE_NOT_ALLOWED, "This role is above what the member's organization role allows.");
    }
}
