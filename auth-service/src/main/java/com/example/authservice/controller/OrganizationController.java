package com.example.authservice.controller;

import com.example.authservice.dto.request.ChangeMemberRoleRequest;
import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.request.InviteMemberRequest;
import com.example.authservice.dto.response.CreatedInvitationResponse;
import com.example.authservice.dto.response.InvitationResponse;
import com.example.authservice.dto.response.MemberResponse;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private static final String ADMIN_OR_SELF = "@orgGuard.hasRole(#orgId, authentication, 'ADMIN')"
            + " or (@orgGuard.isMember(#orgId, authentication) and #userId.equals(principal.id))";

    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        OrganizationResponse organization = organizationService.createOrganization(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/orgs/" + organization.id())).body(organization);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponse>> listMyOrganizations(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(organizationService.findByUser(principal.getId()));
    }

    @GetMapping("/{orgId}")
    @PreAuthorize("@orgGuard.isMember(#orgId, authentication)")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(organizationService.getOrganization(orgId, principal.getId()));
    }

    @PostMapping("/{orgId}/invitations")
    @PreAuthorize("@orgGuard.hasRole(#orgId, authentication, 'ADMIN')")
    public ResponseEntity<CreatedInvitationResponse> inviteMember(
            @PathVariable UUID orgId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        CreatedInvitationResponse invitation = organizationService.inviteMember(orgId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @GetMapping("/{orgId}/invitations")
    @PreAuthorize("@orgGuard.hasRole(#orgId, authentication, 'ADMIN')")
    public ResponseEntity<List<InvitationResponse>> listPendingInvitations(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.listPendingInvitations(orgId));
    }

    @DeleteMapping("/{orgId}/invitations/{invitationId}")
    @PreAuthorize("@orgGuard.hasRole(#orgId, authentication, 'ADMIN')")
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable UUID orgId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal UserPrincipal principal) {

        organizationService.revokeInvitation(orgId, invitationId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orgId}/members")
    @PreAuthorize("@orgGuard.isMember(#orgId, authentication)")
    public ResponseEntity<List<MemberResponse>> listMembers(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.listMembers(orgId));
    }

    @PatchMapping("/{orgId}/members/{userId}")
    @PreAuthorize(ADMIN_OR_SELF)
    public ResponseEntity<MemberResponse> changeMemberRole(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeMemberRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(organizationService.changeMemberRole(orgId, userId, request.role(), principal.getId()));
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    @PreAuthorize(ADMIN_OR_SELF)
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        organizationService.removeMember(orgId, userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
