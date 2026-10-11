package com.example.authservice.controller;

import com.example.authservice.dto.request.CreateOrganizationRequest;
import com.example.authservice.dto.request.InviteMemberRequest;
import com.example.authservice.dto.response.OrganizationResponse;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private OrganizationService organizationService;

    private UserRepository userRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + principal.getId()));

        OrganizationResponse organization = organizationService.createOrganization(request, user);
        return new ResponseEntity<>(organization, HttpStatus.CREATED);
    }

    @PostMapping("/{orgId}/invitations")
    @PreAuthorize("@orgGuard.hasRole(#orgId, authentication, 'ADMIN')")
    public ResponseEntity<Void> inviteMember(
            @PathVariable UUID orgId,
            @Valid @RequestBody InviteMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        User inviter = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + principal.getId()));

        organizationService.inviteMember(orgId, request.email(), request.role(), inviter);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
