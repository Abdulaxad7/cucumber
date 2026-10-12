package com.example.authservice.service.impl;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.AcceptInvitationResponse;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.exception.AlreadyMemberException;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvitationEmailMismatchException;
import com.example.authservice.exception.InvitationGoneException;
import com.example.authservice.exception.InvitationNotFoundException;
import com.example.authservice.exception.SeatLimitReachedException;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);

    private final InvitationRepository invitationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public AcceptInvitationResponse acceptInvitation(String token, UUID userId) {
        return accept(token, userId, null);
    }

    @Override
    @Transactional
    public AcceptInvitationResponse acceptInvitationAsNewUser(String token, AcceptInvitationRequest newAccount) {
        return accept(token, null, newAccount);
    }

    /**
     * The whole use case runs in the caller's transaction, so on any error no account and no membership remain
     * (AC-6.8). Q5: the invitation row is locked and its state checked before any user or membership is written, so
     * the loser of two concurrent acceptances gets what a second, sequential acceptance gets. Q6 order after body
     * validation: {@code INVITATION_NOT_FOUND}; {@code INVITATION_ALREADY_ACCEPTED}, {@code INVITATION_REVOKED},
     * {@code INVITATION_EXPIRED}; then {@code INVITATION_EMAIL_MISMATCH} and {@code ALREADY_MEMBER} for a logged-in
     * caller, or {@code EMAIL_ALREADY_REGISTERED} for an anonymous one; then {@code SEAT_LIMIT_REACHED}.
     *
     * @param callerId   the logged-in caller, or {@code null} for an anonymous caller
     * @param newAccount the anonymous caller's validated account details, or {@code null} for a logged-in caller
     */
    private AcceptInvitationResponse accept(String token, UUID callerId, AcceptInvitationRequest newAccount) {
        Invitation invitation = invitationRepository.findLockedByToken(token)
                .orElseThrow(InvitationNotFoundException::new);
        LocalDateTime now = LocalDateTime.now();
        checkAcceptable(invitation, now);
        String invitedEmail = normalizeEmail(invitation.getEmail());
        Organization organization = invitation.getOrganization();

        User existingUser = null;
        if (callerId != null) {
            existingUser = userRepository.findById(callerId)
                    .orElseThrow(() -> new UsernameNotFoundException("The caller's account no longer exists"));
            if (!normalizeEmail(existingUser.getEmail()).equals(invitedEmail)) {
                throw new InvitationEmailMismatchException();
            }
            if (organization != null && orgMembershipRepository
                    .findByUserIdAndOrganizationId(callerId, organization.getId()).isPresent()) {
                throw new AlreadyMemberException();
            }
        } else if (userRepository.findByEmail(invitedEmail).isPresent()) {
            throw new EmailAlreadyExistsException("An account with the invitation's email already exists");
        }
        if (organization != null) {
            checkFreeSeat(organization.getId());
        }
        User user = existingUser != null ? existingUser : createAccount(invitedEmail, newAccount);

        OrganizationResponse joined = null;
        if (invitation.getOrganization() != null) {
            OrganizationRole role = OrganizationRole.valueOf(invitation.getRole());
            OrgMembership membership = new OrgMembership();
            membership.setOrganization(organization);
            membership.setUser(user);
            membership.setRole(role);
            membership.setCreatedAt(now);
            orgMembershipRepository.save(membership);
            joined = new OrganizationResponse(organization.getId(), organization.getName(), organization.getPlan(),
                    organization.getSeatLimit(), organization.getCreatedAt(), role);
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

        log.info("Invitation {} accepted by user {}{}", invitation.getId(), user.getId(),
                organization != null ? " into organization " + organization.getId() : "");
        return new AcceptInvitationResponse(new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.isMfaEnabled(), user.getCreatedAt()), joined);
    }

    /** Q6: already accepted, then revoked, then expired (by status or by {@code expiresAt}). */
    private static void checkAcceptable(Invitation invitation, LocalDateTime now) {
        switch (invitation.getStatus()) {
            case ACCEPTED -> throw InvitationGoneException.alreadyAccepted();
            case REVOKED -> throw InvitationGoneException.revoked();
            case EXPIRED -> throw InvitationGoneException.expired();
            case PENDING -> {
                if (!invitation.getExpiresAt().isAfter(now)) {
                    throw InvitationGoneException.expired();
                }
            }
        }
    }

    /**
     * D11: accepting needs members &lt; seat limit. The organization row stays locked until the transaction ends, so
     * concurrent invitations and acceptances can't overshoot the limit.
     */
    private void checkFreeSeat(UUID orgId) {
        Organization organization = entityManager.find(Organization.class, orgId, LockModeType.PESSIMISTIC_WRITE);
        Integer seatLimit = organization.getSeatLimit();
        if (seatLimit != null && orgMembershipRepository.countByOrganizationId(orgId) >= seatLimit) {
            throw new SeatLimitReachedException();
        }
    }

    /** Registers the account with the invitation's email, in this transaction (status PENDING_VERIFICATION). */
    private User createAccount(String email, AcceptInvitationRequest newAccount) {
        UserResponse created = authService.register(
                new RegisterRequest(email, newAccount.password(), newAccount.fullName()));
        return userRepository.getReferenceById(created.id());
    }

    /** D12: emails are trimmed and lower-cased before they're compared. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
