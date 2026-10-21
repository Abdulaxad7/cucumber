package com.example.authservice.service.impl;

import com.example.authservice.dto.response.ProjectAccessResponse;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.Project;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private static final String NO_ACCESS = "No access to the project";

    private final ProjectRepository projectRepository;
    private final ProjectRoleService projectRoleService;

    @Override
    @Transactional(readOnly = true)
    public ProjectAccessResponse checkAccess(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AccessDeniedException(NO_ACCESS));
        ProjectRole role = projectRoleService.effectiveRole(userId, project)
                .orElseThrow(() -> new AccessDeniedException(NO_ACCESS));
        return new ProjectAccessResponse(project.getId(), project.getOrganization().getId(), role,
                project.getArchivedAt() != null);
    }
}
