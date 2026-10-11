package com.example.authservice.service.impl;

import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.Plan;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.service.OrganizationService;
import com.example.authservice.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final InvitationRepository invitationRepository;
    private final TokenGenerator tokenGenerator;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, User owner) {
        Organization org = new Organization();
        org.setName(request.name());
        org.setPlan(Plan.FREE);
        
        org.setCreatedAt(LocalDateTime.now());
        Organization savedOrg = organizationRepository.save(org);

        OrgMembership membership = new OrgMembership();
        membership.setOrganization(savedOrg);
        membership.setUser(owner);
        membership.setRole(OrganizationRole.OWNER);
        membership.setCreatedAt(LocalDateTime.now());
        orgMembershipRepository.save(membership);

        // todo
        return OrganizationResponse.builder().build();
    }

    @Override
    public List<OrganizationResponse> findByUser(User user) {
        // ... todo
        return null;
    }

    @Override
    @Transactional
    public void inviteMember(UUID orgId, String email, OrganizationRole role, User inviter) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // TODO: Check for seat limits before inviting

        var accessToken = tokenGenerator.generateNewToken();

        Invitation invitation = Invitation.builder()
                .email(email)
                .organization(organization)
                .role(role.name())
                .invitedBy(inviter)
                .token(accessToken)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        invitationRepository.save(invitation);
    }

    @Override
    public void changeMemberRole(UUID orgId, UUID userId, OrganizationRole newRole) {
        // ...
    }

    @Override
    public void removeMember(UUID orgId, UUID userId) {
        // ...
    }
}
