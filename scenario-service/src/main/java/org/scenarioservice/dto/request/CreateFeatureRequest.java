package org.scenarioservice.dto.request;

import jakarta.validation.constraints.NotEmpty;

/**
 * {@code POST /api/projects/{projectId}/features} (contract {@code CreateFeatureRequest}; D8, D10). No message
 * contains a value. {@link #toString()} shows no content (P16).
 */
public record CreateFeatureRequest(
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

        @MaxCodePoints(value = 500, message = "Message must be at most 500 characters long")
        @StorableText(message = "Message must not contain U+0000 or an unpaired surrogate")
        @NoControlCharacters(trimmed = true, message = "Message must not contain control characters")
        String message) {

    @Override
    public String toString() {
        return "CreateFeatureRequest[]";
    }
}
