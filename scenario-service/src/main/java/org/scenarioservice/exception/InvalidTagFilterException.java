package org.scenarioservice.exception;

/**
 * The {@code tag} filter breaks D19 #5 (given more than once, no tag, whitespace, a control character, or too long):
 * 400 VALIDATION_FAILED with an {@code errors} entry for {@code tag}. The message never contains the value.
 */
public class InvalidTagFilterException extends ApiException {

    public static final String FIELD = "tag";

    private final String reason;

    public InvalidTagFilterException(String reason) {
        super(ErrorCode.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_FAILED_DETAIL);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
