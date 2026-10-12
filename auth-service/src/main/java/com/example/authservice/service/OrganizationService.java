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

    /** The user's organizations with their role, ordered by name. */
    List<OrganizationResponse> findByUser(UUID userId);

    /** One organization with the user's role; the caller must be a member (checked by the guard). */
    OrganizationResponse getOrganization(UUID orgId, UUID userId);

    /** Creates a pending invitation and returns it with its acceptance token, the only time the token is returned. */
    CreatedInvitationResponse inviteMember(UUID orgId, InviteMemberRequest request, UUID inviterId);

    /** Pending, unexpired invitations, oldest first, without tokens. */
    List<InvitationResponse> listPendingInvitations(UUID orgId);

    void revokeInvitation(UUID orgId, UUID invitationId, UUID actorId);

    /** The members, ordered by email. */
    List<MemberResponse> listMembers(UUID orgId);

    MemberResponse changeMemberRole(UUID orgId, UUID userId, OrganizationRole newRole, UUID actorId);

    /** Removes the member; when {@code userId} is the actor, the actor leaves. */
    void removeMember(UUID orgId, UUID userId, UUID actorId);
}
