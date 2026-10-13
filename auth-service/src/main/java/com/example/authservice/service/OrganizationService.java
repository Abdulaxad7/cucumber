package com.example.authservice.service;

import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.request.InviteMemberRequest;
import com.example.authservice.dto.response.CreatedInvitationResponse;
import com.example.authservice.dto.response.InvitationResponse;
import com.example.authservice.dto.response.MemberResponse;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.enums.OrganizationRole;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {
    OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID ownerId);

    List<OrganizationResponse> findByUser(UUID userId);

    OrganizationResponse getOrganization(UUID orgId, UUID userId);

    CreatedInvitationResponse inviteMember(UUID orgId, InviteMemberRequest request, UUID inviterId);

    List<InvitationResponse> listPendingInvitations(UUID orgId);

    void revokeInvitation(UUID orgId, UUID invitationId, UUID actorId);

    List<MemberResponse> listMembers(UUID orgId);

    MemberResponse changeMemberRole(UUID orgId, UUID userId, OrganizationRole newRole, UUID actorId);

    void removeMember(UUID orgId, UUID userId, UUID actorId);
}
