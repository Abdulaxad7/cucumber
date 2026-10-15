package com.example.authservice.service.impl;

import com.example.authservice.dto.request.AddProjectMemberRequest;
import com.example.authservice.dto.request.CreateProjectRequest;
import com.example.authservice.dto.request.RenameProjectRequest;
import com.example.authservice.dto.response.ProjectMemberResponse;
import com.example.authservice.dto.response.ProjectResponse;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.exception.AlreadyProjectMemberException;
import com.example.authservice.exception.NotOrgMemberException;
import com.example.authservice.exception.ProjectArchivedException;
import com.example.authservice.exception.ProjectMemberNotFoundException;
import com.example.authservice.exception.ProjectNameTakenException;
import com.example.authservice.exception.ProjectRoleNotAllowedException;
import com.example.authservice.exception.SelfRoleChangeException;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import com.example.authservice.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final String NO_ACCESS = "No access to the project";

    private static final Comparator<Project> BY_NAME = Comparator
            .comparing((Project project) -> project.getName().toLowerCase(Locale.ROOT))
            .thenComparing(Project::getId);

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final ProjectRoleService projectRoleService;
    private final Clock clock;

    @Override
    @Transactional
    public ProjectResponse createProject(UUID orgId, CreateProjectRequest request, UUID actorId) {
        Organization organization = lockOrganization(orgId);
        String name = request.name().trim();
        requireUniqueName(orgId, name, null);
        Project project = projectRepository.save(Project.builder()
                .organization(organization)
                .name(name)
                .createdAt(Instant.now(clock))
                .build());
        log.info("Project {} created in organization {} by user {}", project.getId(), orgId, actorId);
        return toResponse(project, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UUID orgId, UUID userId) {
        return projectRepository.findByOrganizationId(orgId).stream()
                .sorted(BY_NAME)
                .flatMap(project -> projectRoleService.effectiveRole(userId, project).stream()
                        .map(role -> toResponse(project, role)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID orgId, UUID projectId, UUID userId) {
        return toResponse(findProject(orgId, projectId), userId);
    }

    @Override
    @Transactional
    public ProjectResponse renameProject(UUID orgId, UUID projectId, RenameProjectRequest request, UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        requireActive(project);
        String name = request.name().trim();
        requireUniqueName(orgId, name, projectId);
        project.setName(name);
        log.info("Project {} in organization {} renamed by user {}", projectId, orgId, actorId);
        return toResponse(project, actorId);
    }

    @Override
    @Transactional
    public ProjectResponse archiveProject(UUID orgId, UUID projectId, UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        if (project.getArchivedAt() == null) {
            project.setArchivedAt(Instant.now(clock));
            log.info("Project {} in organization {} archived by user {}", projectId, orgId, actorId);
        }
        return toResponse(project, actorId);
    }

    @Override
    @Transactional
    public ProjectResponse restoreProject(UUID orgId, UUID projectId, UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        if (project.getArchivedAt() != null) {
            project.setArchivedAt(null);
            log.info("Project {} in organization {} restored by user {}", projectId, orgId, actorId);
        }
        return toResponse(project, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID orgId, UUID projectId) {
        Project project = findProject(orgId, projectId);
        return projectMembershipRepository.findByProjectIdOrderByUserEmailAsc(project.getId()).stream()
                .map(ProjectServiceImpl::toMember)
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberResponse addMember(UUID orgId, UUID projectId, AddProjectMemberRequest request,
                                           UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        requireActive(project);
        OrgMembership target = orgMembershipRepository.findByUserIdAndOrganizationId(request.userId(), orgId)
                .orElseThrow(NotOrgMemberException::new);
        if (projectMembershipRepository.findByUserIdAndProjectId(request.userId(), projectId).isPresent()) {
            throw new AlreadyProjectMemberException();
        }
        requireWithinCap(request.role(), target.getRole());
        ProjectMembership membership = projectMembershipRepository.save(ProjectMembership.builder()
                .project(project)
                .user(target.getUser())
                .role(request.role())
                .createdAt(Instant.now(clock))
                .build());
        log.info("User {} added to project {} in organization {} by user {}", request.userId(), projectId, orgId,
                actorId);
        return toMember(membership);
    }

    @Override
    @Transactional
    public ProjectMemberResponse changeMemberRole(UUID orgId, UUID projectId, UUID userId, ProjectRole newRole,
                                                  UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        requireActive(project);
        ProjectMembership target = findMembership(userId, projectId);
        if (userId.equals(actorId)) {
            throw new SelfRoleChangeException();
        }
        requireProjectAdmin(actorId, project);
        OrganizationRole targetOrgRole = orgMembershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .map(OrgMembership::getRole)
                .orElseThrow(ProjectMemberNotFoundException::new);
        requireWithinCap(newRole, targetOrgRole);
        if (target.getRole() != newRole) {
            target.setRole(newRole);
            log.info("Role of user {} in project {} of organization {} changed by user {}", userId, projectId, orgId,
                    actorId);
        }
        return toMember(target);
    }

    @Override
    @Transactional
    public void removeMember(UUID orgId, UUID projectId, UUID userId, UUID actorId) {
        lockOrganization(orgId);
        Project project = findProject(orgId, projectId);
        requireActive(project);
        ProjectMembership target = findMembership(userId, projectId);
        if (!userId.equals(actorId)) {
            requireProjectAdmin(actorId, project);
        }
        projectMembershipRepository.delete(target);
        log.info("User {} removed from project {} in organization {} by user {}", userId, projectId, orgId, actorId);
    }

    private ProjectMembership findMembership(UUID userId, UUID projectId) {
        return projectMembershipRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(ProjectMemberNotFoundException::new);
    }

    private void requireProjectAdmin(UUID actorId, Project project) {
        boolean admin = projectRoleService.effectiveRole(actorId, project)
                .map(role -> role.isAtLeast(ProjectRole.ADMIN))
                .orElse(false);
        if (!admin) {
            throw new AccessDeniedException("Role too low for this operation");
        }
    }

    private static void requireWithinCap(ProjectRole role, OrganizationRole orgRole) {
        ProjectRole cap = orgRole == OrganizationRole.READER ? ProjectRole.READER : ProjectRole.ADMIN;
        if (!cap.isAtLeast(role)) {
            throw new ProjectRoleNotAllowedException();
        }
    }

    private static void requireActive(Project project) {
        if (project.getArchivedAt() != null) {
            throw new ProjectArchivedException();
        }
    }

    private void requireUniqueName(UUID orgId, String trimmedName, UUID exceptProjectId) {
        String key = trimmedName.toLowerCase(Locale.ROOT);
        boolean taken = projectRepository.findByOrganizationId(orgId).stream()
                .filter(other -> !other.getId().equals(exceptProjectId))
                .anyMatch(other -> other.getName() != null
                        && other.getName().trim().toLowerCase(Locale.ROOT).equals(key));
        if (taken) {
            throw new ProjectNameTakenException();
        }
    }

    private Organization lockOrganization(UUID orgId) {
        return organizationRepository.findLockedById(orgId).orElseThrow(() -> new AccessDeniedException(NO_ACCESS));
    }

    private Project findProject(UUID orgId, UUID projectId) {
        return projectRepository.findByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new AccessDeniedException(NO_ACCESS));
    }

    private ProjectResponse toResponse(Project project, UUID userId) {
        ProjectRole myRole = projectRoleService.effectiveRole(userId, project)
                .orElseThrow(() -> new AccessDeniedException(NO_ACCESS));
        return toResponse(project, myRole);
    }

    private static ProjectMemberResponse toMember(ProjectMembership membership) {
        User user = membership.getUser();
        return new ProjectMemberResponse(user.getId(), user.getEmail(), user.getFullName(), membership.getRole(),
                membership.getCreatedAt());
    }

    private static ProjectResponse toResponse(Project project, ProjectRole myRole) {
        return new ProjectResponse(project.getId(), project.getOrganization().getId(), project.getName(),
                project.getCreatedAt(), project.getArchivedAt(), myRole);
    }
}
