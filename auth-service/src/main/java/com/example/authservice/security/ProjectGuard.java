package com.example.authservice.security;

import com.example.authservice.enums.ProjectRole;
import com.example.authservice.service.impl.ProjectRoleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component("projectGuard")
@RequiredArgsConstructor
public class ProjectGuard {

    private static final Logger log = LoggerFactory.getLogger(ProjectGuard.class);

    private final ProjectRoleService projectRoleService;

    public boolean hasRole(UUID projectId, Authentication authentication, String role) {
        Optional<ProjectRole> required = parseRole(role);
        if (required.isEmpty()) {
            return false;
        }
        return callerId(authentication)
                .flatMap(userId -> projectRoleService.getRoleForProject(userId, projectId))
                .map(callerRole -> callerRole.isAtLeast(required.get()))
                .orElse(false);
    }

    public boolean hasRole(UUID orgId, UUID projectId, Authentication authentication, String role) {
        Optional<ProjectRole> required = parseRole(role);
        if (required.isEmpty()) {
            return false;
        }
        return callerId(authentication)
                .flatMap(userId -> projectRoleService.getRoleForProject(userId, orgId, projectId))
                .map(callerRole -> callerRole.isAtLeast(required.get()))
                .orElse(false);
    }

    private static Optional<UUID> callerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.getId());
    }

    private static Optional<ProjectRole> parseRole(String role) {
        if (role == null) {
            log.warn("Project authorization rule without a role: access denied");
            return Optional.empty();
        }
        try {
            return Optional.of(ProjectRole.valueOf(role.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            log.warn("Project authorization rule with an unknown role: access denied");
            return Optional.empty();
        }
    }
}
