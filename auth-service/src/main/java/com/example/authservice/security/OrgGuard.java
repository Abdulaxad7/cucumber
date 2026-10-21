package com.example.authservice.security;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.repository.OrgMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component("orgGuard")
@RequiredArgsConstructor
public class OrgGuard {

    private static final Logger log = LoggerFactory.getLogger(OrgGuard.class);

    private final OrgMembershipRepository orgMembershipRepository;

    public boolean hasRole(UUID orgId, Authentication authentication, String role) {
        Optional<OrganizationRole> required = parseRole(role);
        return required.map(organizationRole -> callerRole(orgId, authentication)
                .map(callerRole -> callerRole.isAtLeast(organizationRole))
                .orElse(false)).orElse(false);
    }

    public boolean isMember(UUID orgId, Authentication authentication) {
        return callerRole(orgId, authentication).isPresent();
    }

    private Optional<OrganizationRole> callerRole(UUID orgId, Authentication authentication) {
        if (orgId == null || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return orgMembershipRepository.findByUserIdAndOrganizationId(principal.getId(), orgId)
                .map(OrgMembership::getRole);
    }

    private static Optional<OrganizationRole> parseRole(String role) {
        if (role == null) {
            log.warn("Organization authorization rule without a role: access denied");
            return Optional.empty();
        }
        try {
            return Optional.of(OrganizationRole.valueOf(role.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            log.warn("Organization authorization rule with an unknown role: access denied");
            return Optional.empty();
        }
    }
}
