package org.scenarioservice.exception;

import java.util.Locale;

/**
 * A valid text with more than the allowed number of elements (D21 #2, order step 7a): 400 VALIDATION_FAILED with an
 * {@code errors} entry for {@code source}. The message names the limit, never the text.
 */
public class FeatureTooLargeException extends ApiException {

    public static final String FIELD = "source";

    private final int limit;

    public FeatureTooLargeException(int limit) {
        super(ErrorCode.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_FAILED_DETAIL);
        this.limit = limit;
    }

    public String reason() {
        return String.format(Locale.ROOT, "Source must have at most %,d elements", limit);
    }
}
