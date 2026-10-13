package com.example.authservice.controller;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.request.LoginRequest;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.AcceptInvitationResponse;
import com.example.authservice.dto.response.LoginResponse;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.AcceptInvitationResult;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication Controller")
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;

    @PostMapping("/register")
    @Operation(description = "Registering new user")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserResponse userResponse = authService.register(registerRequest);

        return new ResponseEntity<>(userResponse, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @PathVariable String token,
            @RequestBody(required = false) AcceptInvitationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        AcceptInvitationResult result = invitationService.accept(token,
                principal != null ? principal.getId() : null, request);
        return ResponseEntity.status(result.accountCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

}
