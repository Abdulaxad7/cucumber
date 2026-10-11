package com.example.authservice.service;

import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.User;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {
    OrganizationResponse createOrganization(CreateOrganizationRequest request, User owner);

    List<OrganizationResponse> findByUser(User user);

    void inviteMember(UUID orgId, String email, OrganizationRole role, User inviter);

    void changeMemberRole(UUID orgId, UUID userId, OrganizationRole newRole);

    void removeMember(UUID orgId, UUID userId);
}
