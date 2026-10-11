package com.example.authservice.controller;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.request.LoginRequest;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.LoginResponse;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final UserRepository userRepository;

    @PostMapping("/register")
    @Operation(description = "Registering new user")
    @ApiResponses({
            @ApiResponse(useReturnTypeSchema = true, description = "Api v1"),
            @ApiResponse
    })
    public ResponseEntity<@NonNull UserResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserResponse userResponse = authService.register(registerRequest);

        return new ResponseEntity<>(userResponse, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<@NonNull LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody(required = false) AcceptInvitationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UserResponse userResponse = null;
        User user;
        if (principal != null) {
            user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        } else {
            if (request == null) {
                return ResponseEntity.badRequest().body("Request body is required for new users.");
            }
            RegisterRequest registerRequest = new RegisterRequest(
                    invitationService.findByToken(token).getEmail(),
                    request.password(),
                    request.fullName()
            );
            userResponse = authService.register(registerRequest);
            user = userRepository.findById(userResponse.id()).get(); // Re-fetch the managed entity
        }

        invitationService.acceptInvitation(token, user);

        if (userResponse != null) {
            return new ResponseEntity<>(userResponse, HttpStatus.CREATED);
        } else {
            return ResponseEntity.ok().build();
        }
    }

}
