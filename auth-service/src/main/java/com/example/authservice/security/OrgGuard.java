package com.example.authservice.security;

import com.example.authservice.enums.OrganizationRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("orgGuard")
public class OrgGuard {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean hasRole(UUID orgId, Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            // This might happen if another authentication mechanism is used.
            // For now, we only support JWT-based authorization for this guard.
            return false;
        }

        List<Map<String, Object>> orgs = jwt.getClaim("orgs");
        if (orgs == null) {
            return false;
        }

        OrganizationRole requiredRole = OrganizationRole.valueOf(role.toUpperCase());

        return orgs.stream()
                .anyMatch(org -> {
                    String id = org.get("org_id").toString();
                    String orgRole = org.get("role").toString();
                    return orgId.toString().equals(id) && OrganizationRole.valueOf(orgRole).ordinal() >= requiredRole.ordinal();
                });
    }
}
