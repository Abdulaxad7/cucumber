package com.example.authservice.dto.response;

import com.example.authservice.enums.OrganizationRole;

import java.util.UUID;

public record OrgClaim(
        UUID orgId,
        OrganizationRole role
) {
    public OrgClaim(UUID orgId, OrganizationRole role) {
        this.orgId = orgId;
        this.role = role;
    }
}
