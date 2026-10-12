package com.example.authservice.exception;

/**
 * Base class of every business error auth-service throws (P6). It carries the D2 {@link ErrorCode}, which fixes
 * the HTTP status, and a {@code detail} that is safe to show to the caller: it must never contain internals.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;

    protected ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /** The Problem's {@code detail}. */
    public String detail() {
        return getMessage();
    }
}
