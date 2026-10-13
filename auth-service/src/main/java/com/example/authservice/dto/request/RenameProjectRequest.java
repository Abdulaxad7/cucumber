package com.example.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameProjectRequest(
        @NotBlank(message = "Project name cannot be blank")
        @Size(max = 100, message = "Project name must be at most 100 characters long")
        String name) {
}
