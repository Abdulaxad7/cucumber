package com.example.authservice.service.impl;

import com.example.authservice.configuration.OrganizationProperties;
import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.request.InviteMemberRequest;
import com.example.authservice.dto.response.CreatedInvitationResponse;
import com.example.authservice.dto.response.InvitationResponse;
import com.example.authservice.dto.response.MemberResponse;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.Plan;
import com.example.authservice.exception.AlreadyMemberException;
import com.example.authservice.exception.InvitationNotFoundException;
import com.example.authservice.exception.InvitationNotPendingException;
import com.example.authservice.exception.InvitationPendingException;
import com.example.authservice.exception.LastOwnerException;
import com.example.authservice.exception.MemberNotFoundException;
import com.example.authservice.exception.OrgRoleCeilingException;
import com.example.authservice.exception.SeatLimitReachedException;
import com.example.authservice.exception.SelfRoleChangeException;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.OrganizationService;
import com.example.authservice.util.TokenGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OrganizationProperties.class)
public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationServiceImpl.class);
    private static final int INVITATION_VALIDITY_DAYS = 7;
    private static final String ROLE_CEILING_DETAIL = "You can't grant, change or remove a role above your own.";

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final OrganizationProperties organizationProperties;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID ownerId) {
        LocalDateTime now = LocalDateTime.now();
        Organization org = new Organization();
        org.setName(request.name());
        org.setPlan(Plan.FREE);
        org.setSeatLimit(organizationProperties.freeSeatLimit());
        org.setCreatedAt(now);
        Organization savedOrg = organizationRepository.save(org);

        OrgMembership membership = new OrgMembership();
        membership.setOrganization(savedOrg);
        membership.setUser(userRepository.getReferenceById(ownerId));
        membership.setRole(OrganizationRole.OWNER);
        membership.setCreatedAt(now);
        orgMembershipRepository.save(membership);

        log.info("Organization {} created by user {}", savedOrg.getId(), ownerId);
        return toResponse(savedOrg, OrganizationRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> findByUser(UUID userId) {
        return orgMembershipRepository.findByUserIdOrderByOrganizationNameAscOrganizationIdAsc(userId).stream()
                .map(membership -> toResponse(membership.getOrganization(), membership.getRole()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID orgId, UUID userId) {
        // The guard has checked membership; a removal in between gets the guard's answer (D3).
        OrgMembership membership = orgMembershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of the organization"));
        return toResponse(membership.getOrganization(), membership.getRole());
    }

    /**
     * Checks run in the Q6 order after the guard: {@code ROLE_CEILING}, {@code ALREADY_MEMBER},
     * {@code INVITATION_PENDING}, {@code SEAT_LIMIT_REACHED}. The organization row stays locked until the transaction
     * ends, so concurrent invitations and acceptances can't overshoot the seat limit.
     */
    @Override
    @Transactional
    public CreatedInvitationResponse inviteMember(UUID orgId, InviteMemberRequest request, UUID inviterId) {
        Organization organization = lockOrganization(orgId);
        OrganizationRole inviterRole = roleOf(inviterId, orgId);
        if (!inviterRole.isAtLeast(request.role())) {
            throw new OrgRoleCeilingException(ROLE_CEILING_DETAIL);
        }
        String email = normalizeEmail(request.email());
        LocalDateTime now = LocalDateTime.now();
        if (orgMembershipRepository.existsByOrganizationIdAndUserEmail(orgId, email)) {
            throw new AlreadyMemberException();
        }
        if (invitationRepository.existsByOrganizationIdAndEmailAndStatusAndExpiresAtAfter(
                orgId, email, InvitationStatus.PENDING, now)) {
            throw new InvitationPendingException();
        }
        if (organization.getSeatLimit() != null && seatsUsed(orgId, now) >= organization.getSeatLimit()) {
            throw new SeatLimitReachedException();
        }

        Invitation invitation = invitationRepository.save(Invitation.builder()
                .email(email)
                .organization(organization)
                .role(request.role().name())
                .invitedBy(userRepository.getReferenceById(inviterId))
                .token(tokenGenerator.generateNewToken())
                .status(InvitationStatus.PENDING)
                .expiresAt(now.plusDays(INVITATION_VALIDITY_DAYS))
                .createdAt(now)
                .build());

        log.info("Invitation {} created in organization {} by user {}", invitation.getId(), orgId, inviterId);
        return new CreatedInvitationResponse(invitation.getId(), invitation.getEmail(), request.role(),
                invitation.getStatus(), invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.getToken());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> listPendingInvitations(UUID orgId) {
        return invitationRepository.findByOrganizationIdAndStatusAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
                        orgId, InvitationStatus.PENDING, LocalDateTime.now()).stream()
                .map(invitation -> new InvitationResponse(invitation.getId(), invitation.getEmail(),
                        OrganizationRole.valueOf(invitation.getRole()), invitation.getStatus(),
                        invitation.getCreatedAt(), invitation.getExpiresAt()))
                .toList();
    }

    /**
     * A {@code PENDING} invitation becomes {@code REVOKED}, even when it has already expired (Q4). Accepted or revoked
     * invitations get {@code INVITATION_NOT_PENDING}. D13 order after the guard: {@code INVITATION_NOT_FOUND},
     * {@code INVITATION_NOT_PENDING}, then {@code ROLE_CEILING} (AC-5.12: the invitation's role must rank at most the
     * actor's).
     */
    @Override
    @Transactional
    public void revokeInvitation(UUID orgId, UUID invitationId, UUID actorId) {
        Invitation invitation = invitationRepository.findLockedByIdAndOrganizationId(invitationId, orgId)
                .orElseThrow(InvitationNotFoundException::new);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException();
        }
        if (!roleOf(actorId, orgId).isAtLeast(OrganizationRole.valueOf(invitation.getRole()))) {
            throw new OrgRoleCeilingException(ROLE_CEILING_DETAIL);
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        log.info("Invitation {} in organization {} revoked by user {}", invitationId, orgId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID orgId) {
        return orgMembershipRepository.findByOrganizationIdOrderByUserEmailAsc(orgId).stream()
                .map(OrganizationServiceImpl::toMember)
                .toList();
    }

    /**
     * Q6 order after the guard: {@code MEMBER_NOT_FOUND}, {@code SELF_ROLE_CHANGE}, {@code ROLE_CEILING} (both the
     * target's current role and the new role must rank at most the actor's). Setting the current role is a no-op.
     */
    @Override
    @Transactional
    public MemberResponse changeMemberRole(UUID orgId, UUID userId, OrganizationRole newRole, UUID actorId) {
        lockOrganization(orgId);
        OrganizationRole actorRole = roleOf(actorId, orgId);
        OrgMembership target = orgMembershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(MemberNotFoundException::new);
        if (userId.equals(actorId)) {
            throw new SelfRoleChangeException();
        }
        requireAdmin(actorRole);
        if (!actorRole.isAtLeast(target.getRole()) || !actorRole.isAtLeast(newRole)) {
            throw new OrgRoleCeilingException(ROLE_CEILING_DETAIL);
        }
        if (target.getRole() != newRole) {
            target.setRole(newRole);
            log.info("Role of user {} in organization {} changed by user {}", userId, orgId, actorId);
        }
        return toMember(target);
    }

    /**
     * Q6 order after the guard: {@code MEMBER_NOT_FOUND}; then, for someone else, {@code ROLE_CEILING}, or, when
     * leaving, {@code LAST_OWNER}. The organization row is locked, so two owners can't both leave at once.
     */
    @Override
    @Transactional
    public void removeMember(UUID orgId, UUID userId, UUID actorId) {
        lockOrganization(orgId);
        OrganizationRole actorRole = roleOf(actorId, orgId);
        OrgMembership target = orgMembershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .orElseThrow(MemberNotFoundException::new);
        if (userId.equals(actorId)) {
            if (target.getRole() == OrganizationRole.OWNER
                    && orgMembershipRepository.countByOrganizationIdAndRole(orgId, OrganizationRole.OWNER) <= 1) {
                throw new LastOwnerException();
            }
        } else {
            requireAdmin(actorRole);
            if (!actorRole.isAtLeast(target.getRole())) {
                throw new OrgRoleCeilingException(ROLE_CEILING_DETAIL);
            }
        }
        orgMembershipRepository.delete(target);
        log.info("User {} removed from organization {} by user {}", userId, orgId, actorId);
    }

    /** Locks the organization row until the transaction ends; seat-counting writers serialise on it. */
    private Organization lockOrganization(UUID orgId) {
        Organization organization = entityManager.find(Organization.class, orgId, LockModeType.PESSIMISTIC_WRITE);
        if (organization == null) {
            throw new AccessDeniedException("Not a member of the organization");
        }
        return organization;
    }

    /** The caller's role; the guard has checked membership, so a missing one means it was removed in between. */
    private OrganizationRole roleOf(UUID userId, UUID orgId) {
        return orgMembershipRepository.findByUserIdAndOrganizationId(userId, orgId)
                .map(OrgMembership::getRole)
                .orElseThrow(() -> new AccessDeniedException("Not a member of the organization"));
    }

    /** The guard checked the role; a demotion in between gets the guard's answer (D3). */
    private static void requireAdmin(OrganizationRole actorRole) {
        if (!actorRole.isAtLeast(OrganizationRole.ADMIN)) {
            throw new AccessDeniedException("Role too low for this operation");
        }
    }

    /** D11: members plus pending invitations that haven't expired. */
    private long seatsUsed(UUID orgId, LocalDateTime now) {
        return orgMembershipRepository.countByOrganizationId(orgId)
                + invitationRepository.countByOrganizationIdAndStatusAndExpiresAtAfter(
                        orgId, InvitationStatus.PENDING, now);
    }

    /** D12: emails are trimmed and lower-cased before they're stored or compared; validation runs on the raw input. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static MemberResponse toMember(OrgMembership membership) {
        User user = membership.getUser();
        return new MemberResponse(user.getId(), user.getEmail(), user.getFullName(), membership.getRole(),
                membership.getCreatedAt());
    }

    private static OrganizationResponse toResponse(Organization organization, OrganizationRole myRole) {
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getPlan(),
                organization.getSeatLimit(), organization.getCreatedAt(), myRole);
    }
}
