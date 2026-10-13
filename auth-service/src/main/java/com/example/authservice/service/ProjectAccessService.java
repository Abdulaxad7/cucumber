package com.example.authservice.service;

import com.example.authservice.dto.response.ProjectAccessResponse;

import java.util.UUID;

public interface ProjectAccessService {

    ProjectAccessResponse checkAccess(UUID projectId, UUID userId);
}
