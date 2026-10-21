package com.example.authservice.dto.request;

import com.example.authservice.enums.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(
        @NotNull(message = "Role is required")
        OrganizationRole role
) {
}
