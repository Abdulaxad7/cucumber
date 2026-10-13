package com.example.authservice.exception;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;

    protected ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }
}
