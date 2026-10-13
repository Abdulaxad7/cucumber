package com.example.authservice.controller;

import com.example.authservice.dto.request.AddProjectMemberRequest;
import com.example.authservice.dto.request.ChangeProjectMemberRoleRequest;
import com.example.authservice.dto.request.CreateProjectRequest;
import com.example.authservice.dto.request.RenameProjectRequest;
import com.example.authservice.dto.response.ProjectMemberResponse;
import com.example.authservice.dto.response.ProjectResponse;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orgs/{orgId}/projects")
@RequiredArgsConstructor
@Tag(name = "Projects")
public class ProjectController {

    private static final String HAS_ACCESS = "@projectGuard.hasRole(#orgId, #projectId, authentication, 'READER')";
    private static final String PROJECT_ADMIN = "@projectGuard.hasRole(#orgId, #projectId, authentication, 'ADMIN')";

    private static final String PROJECT_ADMIN_OR_SELF = PROJECT_ADMIN + " or (" + HAS_ACCESS
            + " and #userId.equals(principal.id))";

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("@orgGuard.hasRole(#orgId, authentication, 'ADMIN')")
    @Operation(description = "Create a project (organization OWNER or ADMIN)")
    public ResponseEntity<ProjectResponse> createProject(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        ProjectResponse project = projectService.createProject(orgId, request, principal.getId());
        return ResponseEntity.created(URI.create("/api/orgs/" + project.orgId() + "/projects/" + project.id()))
                .body(project);
    }

    @GetMapping
    @PreAuthorize("@orgGuard.isMember(#orgId, authentication)")
    @Operation(description = "The organization's projects the caller has access to")
    public ResponseEntity<List<ProjectResponse>> listProjects(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.listProjects(orgId, principal.getId()));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize(HAS_ACCESS)
    @Operation(description = "View one project with the caller's effective role")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.getProject(orgId, projectId, principal.getId()));
    }

    @PatchMapping("/{projectId}")
    @PreAuthorize(PROJECT_ADMIN)
    @Operation(description = "Rename a project (effective project ADMIN)")
    public ResponseEntity<ProjectResponse> renameProject(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @Valid @RequestBody RenameProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.renameProject(orgId, projectId, request, principal.getId()));
    }

    @PostMapping("/{projectId}/archive")
    @PreAuthorize(PROJECT_ADMIN)
    @Operation(description = "Archive a project (effective project ADMIN); idempotent")
    public ResponseEntity<ProjectResponse> archiveProject(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.archiveProject(orgId, projectId, principal.getId()));
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize(PROJECT_ADMIN)
    @Operation(description = "Restore an archived project (effective project ADMIN); idempotent")
    public ResponseEntity<ProjectResponse> restoreProject(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.restoreProject(orgId, projectId, principal.getId()));
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize(HAS_ACCESS)
    @Operation(description = "The project's explicit members, ordered by email")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.listMembers(orgId, projectId));
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize(PROJECT_ADMIN)
    @Operation(description = "Add an organization member to the project (effective project ADMIN)")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMember(orgId, projectId, request, principal.getId()));
    }

    @PatchMapping("/{projectId}/members/{userId}")
    @PreAuthorize(PROJECT_ADMIN_OR_SELF)
    @Operation(description = "Change a project member's role (effective project ADMIN)")
    public ResponseEntity<ProjectMemberResponse> changeMemberRole(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeProjectMemberRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                projectService.changeMemberRole(orgId, projectId, userId, request.role(), principal.getId()));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize(PROJECT_ADMIN_OR_SELF)
    @Operation(description = "Remove a project member (effective project ADMIN), or leave the project")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectService.removeMember(orgId, projectId, userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
