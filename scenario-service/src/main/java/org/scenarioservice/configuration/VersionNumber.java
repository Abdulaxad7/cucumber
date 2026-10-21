package org.scenarioservice.configuration;

/**
 * The {@code {version}} path parameter (D19 #6): a positive int32, spelled {@code ^[1-9][0-9]{0,9}$}. Bound only by
 * {@link StrictParameterBinding}, so leading zeros, a sign, whitespace or overflow are 400 MALFORMED_REQUEST.
 */
public record VersionNumber(int value) {
}
