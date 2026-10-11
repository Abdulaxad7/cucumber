package com.example.authservice.security;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.User;
import com.example.authservice.service.impl.ProjectRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectGuard {

    private final ProjectRoleService projectRoleService;

    public boolean hasRole(UUID projectId, Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        User user = (User) authentication.getPrincipal();
        ProjectRole requiredRole = ProjectRole.valueOf(role.toUpperCase());

        ProjectRole userRole = projectRoleService.getRoleForProject(Objects.requireNonNull(user).getId(), projectId);

        return userRole != null && userRole.ordinal() >= requiredRole.ordinal();
    }

    @Cacheable(value = "project-roles", key = "#userId + '-' + #projectId")
    public ProjectRole getRoleForProject(UUID userId, UUID projectId) {
        return projectRoleService.getRoleForProject(userId, projectId);
    }
}
