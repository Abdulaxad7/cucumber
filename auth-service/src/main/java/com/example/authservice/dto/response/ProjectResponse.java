package com.example.authservice.dto.response;

import com.example.authservice.enums.ProjectRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID orgId, String name, LocalDateTime createdAt, LocalDateTime archivedAt,
                              ProjectRole myRole) {
}
