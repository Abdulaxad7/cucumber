package com.example.authservice.dto.response;

import com.example.authservice.enums.ProjectRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectMemberResponse(UUID userId, String email, String fullName, ProjectRole role,
                                    LocalDateTime joinedAt) {
}
