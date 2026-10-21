package com.example.authservice.service.impl;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.AcceptInvitationResponse;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.exception.AlreadyMemberException;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvitationEmailMismatchException;
import com.example.authservice.exception.InvitationGoneException;
import com.example.authservice.exception.InvitationNotFoundException;
import com.example.authservice.exception.RequestValidationException;
import com.example.authservice.exception.RequestValidationException.FieldViolation;
import com.example.authservice.exception.SeatLimitReachedException;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import com.example.authservice.repository.InvitationRepository;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.AcceptInvitationResult;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import com.example.authservice.util.EmailNormalizer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationServiceImpl.class);

    private final InvitationRepository invitationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final Validator validator;
    private final Clock clock;

    @Override
    @Transactional
    public AcceptInvitationResult accept(String token, UUID callerId, AcceptInvitationRequest newAccount) {
        if (callerId != null) {
            return new AcceptInvitationResult(acceptValidated(token, callerId, null), false);
        }
        return new AcceptInvitationResult(acceptValidated(token, null, validNewAccount(newAccount)), true);
    }

    private AcceptInvitationRequest validNewAccount(AcceptInvitationRequest newAccount) {
        AcceptInvitationRequest details = newAccount != null ? newAccount : new AcceptInvitationRequest(null, null);
        Set<ConstraintViolation<AcceptInvitationRequest>> violations = validator.validate(details);
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations.stream()
                    .map(violation -> new FieldViolation(violation.getPropertyPath().toString(),
                            violation.getMessage()))
                    .toList());
        }
        return details;
    }

    private AcceptInvitationResponse acceptValidated(String token, UUID callerId, AcceptInvitationRequest newAccount) {
        Invitation invitation = invitationRepository.findLockedByToken(token)
                .orElseThrow(InvitationNotFoundException::new);
        Instant now = Instant.now(clock);
        checkAcceptable(invitation, now);
        String invitedEmail = EmailNormalizer.normalize(invitation.getEmail());
        Organization organization = invitation.getOrganization();

        User existingUser = null;
        if (callerId != null) {
            existingUser = userRepository.findById(callerId)
                    .orElseThrow(() -> new UsernameNotFoundException("The caller's account no longer exists"));
            if (!EmailNormalizer.normalize(existingUser.getEmail()).equals(invitedEmail)) {
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
            OrganizationRole role = invitation.getRole();
            OrgMembership membership = new OrgMembership();
            membership.setOrganization(organization);
            membership.setUser(user);
            membership.setRole(role);
            membership.setCreatedAt(now);
            orgMembershipRepository.save(membership);
            joined = new OrganizationResponse(organization.getId(), organization.getName(), organization.getPlan(),
                    organization.getSeatLimit(), organization.getCreatedAt(), role);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("Invitation {} accepted by user {}{}", invitation.getId(), user.getId(),
                organization != null ? " into organization " + organization.getId() : "");
        return new AcceptInvitationResponse(new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.isMfaEnabled(), user.getCreatedAt()), joined);
    }

    private static void checkAcceptable(Invitation invitation, Instant now) {
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

    private void checkFreeSeat(UUID orgId) {
        Organization organization = organizationRepository.findLockedById(orgId).orElseThrow();
        Integer seatLimit = organization.getSeatLimit();
        if (seatLimit != null && orgMembershipRepository.countByOrganizationId(orgId) >= seatLimit) {
            throw new SeatLimitReachedException();
        }
    }

    private User createAccount(String email, AcceptInvitationRequest newAccount) {
        UserResponse created = authService.register(
                new RegisterRequest(email, newAccount.password(), newAccount.fullName()));
        return userRepository.getReferenceById(created.id());
    }
}
