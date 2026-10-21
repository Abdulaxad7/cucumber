package com.example.authservice.service;

import com.example.authservice.dto.request.AddProjectMemberRequest;
import com.example.authservice.dto.request.CreateProjectRequest;
import com.example.authservice.dto.request.RenameProjectRequest;
import com.example.authservice.dto.response.ProjectMemberResponse;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.dto.response.ProjectResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectService {

    ProjectResponse createProject(UUID orgId, CreateProjectRequest request, UUID actorId);

    List<ProjectResponse> listProjects(UUID orgId, UUID userId);

    ProjectResponse getProject(UUID orgId, UUID projectId, UUID userId);

    ProjectResponse renameProject(UUID orgId, UUID projectId, RenameProjectRequest request, UUID actorId);

    ProjectResponse archiveProject(UUID orgId, UUID projectId, UUID actorId);

    ProjectResponse restoreProject(UUID orgId, UUID projectId, UUID actorId);

    List<ProjectMemberResponse> listMembers(UUID orgId, UUID projectId);

    ProjectMemberResponse addMember(UUID orgId, UUID projectId, AddProjectMemberRequest request, UUID actorId);

    ProjectMemberResponse changeMemberRole(UUID orgId, UUID projectId, UUID userId, ProjectRole newRole, UUID actorId);

    void removeMember(UUID orgId, UUID projectId, UUID userId, UUID actorId);
}
