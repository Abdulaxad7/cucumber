package com.example.authservice.service.impl;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectRoleService {

    private final ProjectRepository projectRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;

    @Transactional(readOnly = true)
    public Optional<ProjectRole> getRoleForProject(UUID userId, UUID projectId) {
        if (userId == null || projectId == null) {
            return Optional.empty();
        }
        return projectRepository.findById(projectId).flatMap(project -> effectiveRole(userId, project));
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRole> getRoleForProject(UUID userId, UUID orgId, UUID projectId) {
        if (userId == null || orgId == null || projectId == null) {
            return Optional.empty();
        }
        return projectRepository.findById(projectId)
                .filter(project -> orgId.equals(project.getOrganization().getId()))
                .flatMap(project -> effectiveRole(userId, project));
    }

    public Optional<ProjectRole> effectiveRole(UUID userId, Project project) {
        Optional<OrganizationRole> orgRole = orgMembershipRepository
                .findByUserIdAndOrganizationId(userId, project.getOrganization().getId())
                .map(OrgMembership::getRole);
        if (orgRole.isEmpty()) {
            return Optional.empty();
        }
        if (orgRole.get().isAtLeast(OrganizationRole.ADMIN)) {
            return Optional.of(ProjectRole.ADMIN);
        }
        return projectMembershipRepository.findByUserIdAndProjectId(userId, project.getId())
                .map(ProjectMembership::getRole)
                .map(explicitRole -> withinCap(explicitRole, orgRole.get()));
    }

    private static ProjectRole withinCap(ProjectRole explicitRole, OrganizationRole orgRole) {
        return orgRole == OrganizationRole.READER ? ProjectRole.READER : explicitRole;
    }
}
