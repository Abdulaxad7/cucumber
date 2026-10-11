package com.example.authservice.service.impl;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;

    @Override
    public Invitation findByToken(String token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, User user) {
        Invitation invitation = findByToken(token);

        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invitation is invalid or has expired");
        }

        if (invitation.getOrganization() != null) {
            // TODO: Check seat limits
            OrgMembership membership = new OrgMembership();
            membership.setOrganization(invitation.getOrganization());
            membership.setUser(user);
            membership.setRole(OrganizationRole.valueOf(invitation.getRole()));
            membership.setCreatedAt(LocalDateTime.now());
            orgMembershipRepository.save(membership);
        } else if (invitation.getProject() != null) {
            // TODO: Check org role ceiling
            ProjectMembership membership = new ProjectMembership();
            membership.setProject(invitation.getProject());
            membership.setUser(user);
            membership.setRole(ProjectRole.valueOf(invitation.getRole()));
            membership.setCreatedAt(LocalDateTime.now());
            projectMembershipRepository.save(membership);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
    }
}
