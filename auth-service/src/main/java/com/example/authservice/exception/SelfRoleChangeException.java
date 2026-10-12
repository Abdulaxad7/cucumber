package com.example.authservice.exception;

/** 422 {@code SELF_ROLE_CHANGE}: nobody can change their own role (D11). */
public class SelfRoleChangeException extends ApiException {

    public SelfRoleChangeException() {
        super(ErrorCode.SELF_ROLE_CHANGE, "You can't change your own role.");
    }
}
