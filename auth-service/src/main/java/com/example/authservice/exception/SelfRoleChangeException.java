package com.example.authservice.exception;

public class SelfRoleChangeException extends ApiException {

    public SelfRoleChangeException() {
        super(ErrorCode.SELF_ROLE_CHANGE, "You can't change your own role.");
    }
}
