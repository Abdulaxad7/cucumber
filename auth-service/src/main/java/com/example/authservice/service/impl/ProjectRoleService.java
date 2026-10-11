package com.example.authservice.service.impl;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.repository.ProjectMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectRoleService {

    private final ProjectMembershipRepository repository;

    @Cacheable(value = "project-roles", key = "#userId + '-' + #projectId")
    public ProjectRole getRoleForProject(UUID userId, UUID projectId) {
        return repository.findByUserIdAndProjectId(userId, projectId)
                .map(ProjectMembership::getRole)
                .orElse(null);
    }
}