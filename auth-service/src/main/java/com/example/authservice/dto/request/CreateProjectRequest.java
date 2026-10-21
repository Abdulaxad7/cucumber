package com.example.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank(message = "Project name cannot be blank")
        @VisibleText(message = "Project name cannot be blank")
        @MaxCodePoints(value = 100, message = "Project name must be at most 100 characters long")
        @StorableText(trimmed = true, message = "Project name must not contain control characters or invalid Unicode characters")
        String name) {
}
