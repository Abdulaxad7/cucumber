package com.example.authservice.dto.response;

import com.example.authservice.enums.OrganizationRole;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrgClaim(
        UUID orgId,
        OrganizationRole role
) {
    public OrgClaim(UUID orgId, OrganizationRole role) {
        this.orgId = orgId;
        this.role = role;
    }
}
