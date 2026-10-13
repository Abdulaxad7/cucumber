package com.example.authservice.dto.response;

import com.example.authservice.enums.ProjectRole;

import java.util.UUID;

public record ProjectAccessResponse(UUID projectId, UUID orgId, ProjectRole role, boolean archived) {
}
