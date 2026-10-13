package com.example.authservice.dto.request;

import com.example.authservice.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddProjectMemberRequest(
        @NotNull(message = "User id is required")
        UUID userId,
        @NotNull(message = "Role is required")
        ProjectRole role) {
}
