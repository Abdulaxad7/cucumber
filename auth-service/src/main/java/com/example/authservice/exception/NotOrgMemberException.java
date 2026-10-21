package com.example.authservice.exception;

public class NotOrgMemberException extends ApiException {

    public NotOrgMemberException() {
        super(ErrorCode.NOT_ORG_MEMBER, "Only members of the project's organization can be added.");
    }
}
