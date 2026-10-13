package com.example.authservice.dto.request;

import com.example.authservice.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ChangeProjectMemberRoleRequest(
        @NotNull(message = "Role is required")
        ProjectRole role) {
}
