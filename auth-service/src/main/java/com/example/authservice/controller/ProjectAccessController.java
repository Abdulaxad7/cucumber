package com.example.authservice.controller;

import com.example.authservice.dto.response.ProjectAccessResponse;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/projects")
@RequiredArgsConstructor
@Tag(name = "Project access check")
public class ProjectAccessController {

    private final ProjectAccessService projectAccessService;

    @GetMapping("/{projectId}/access")
    @PreAuthorize("@projectGuard.hasRole(#projectId, authentication, 'READER')")
    @Operation(description = "The caller's effective role in the project, for other services")
    public ResponseEntity<ProjectAccessResponse> checkAccess(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectAccessService.checkAccess(projectId, principal.getId()));
    }
}
