package com.example.authservice.exception;

import java.util.List;

public class RequestValidationException extends ApiException {

    private final List<FieldViolation> violations;

    public RequestValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_FAILED_DETAIL);
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public record FieldViolation(String field, String message) {
    }
}
