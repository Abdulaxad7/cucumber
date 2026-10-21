package com.example.authservice.dto.response;

import com.example.authservice.enums.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID orgId, String name, Instant createdAt, Instant archivedAt,
                              ProjectRole myRole) {
}
