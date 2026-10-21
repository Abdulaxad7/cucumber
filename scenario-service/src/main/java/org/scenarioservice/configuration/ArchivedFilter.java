package org.scenarioservice.configuration;

/**
 * The {@code archived} query parameter (D19 #6): exactly {@code true} or {@code false}. Bound only by
 * {@link StrictParameterBinding}, so {@code yes}, {@code 1}, {@code True}, an empty value or whitespace is 400
 * MALFORMED_REQUEST.
 */
public record ArchivedFilter(boolean value) {
}
