package org.scenarioservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code PUT /api/projects/{projectId}/features/{featureId}} (contract {@code SaveFeatureRequest}; D9): the feature's
 * full new state and the version the client edited. {@code baseVersion} must be a JSON integer (strict JSON, D19 #6).
 * {@link #toString()} shows no content (P16).
 */
public record SaveFeatureRequest(
        @NotEmpty(message = "Path is required")
        @MaxCodePoints(value = 1024, message = "Path must be at most 1024 characters long")
        @StorableText(message = "Path must not contain U+0000 or an unpaired surrogate")
        @NoControlCharacters(message = "Path must not contain control characters")
        @VisiblePathParts(message = "Path must not start with, or have a part made only of, invisible characters")
        @FeaturePath(message = "Path must be a relative path of segments separated by / that ends in .feature")
        String path,

        @NotEmpty(message = "Source is required")
        @MaxCodePoints(value = 1_000_000, message = "Source must be at most 1000000 characters long")
        @StorableText(message = "Source must not contain U+0000 or an unpaired surrogate")
        String source,

        @NotNull(message = "Base version is required")
        @Positive(message = "Base version must be at least 1")
        Integer baseVersion,

        @MaxCodePoints(value = 500, message = "Message must be at most 500 characters long")
        @StorableText(message = "Message must not contain U+0000 or an unpaired surrogate")
        @NoControlCharacters(trimmed = true, message = "Message must not contain control characters")
        String message) {

    @Override
    public String toString() {
        return "SaveFeatureRequest[baseVersion=" + baseVersion + "]";
    }
}
