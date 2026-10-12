package com.example.authservice.controller;

import com.example.authservice.dto.request.AcceptInvitationRequest;
import com.example.authservice.dto.request.LoginRequest;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.AcceptInvitationResponse;
import com.example.authservice.dto.response.LoginResponse;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
    private final Validator validator;

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
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody(required = false) AcceptInvitationRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal UserPrincipal principal) throws MethodArgumentNotValidException {

        if (principal != null) {
            // Q3: a logged-in caller's identity comes from the token, so a well-formed body is ignored, valid or not.
            return ResponseEntity.ok(invitationService.acceptInvitation(token, principal.getId()));
        }
        // Q3, AC-6.6: an anonymous caller needs a valid body; with the BindingResult parameter Spring doesn't throw.
        BindingResult errors = bindingResult;
        if (request == null) {
            // AC-6.10 (D13, Q2): a missing or empty body is validated like {}, so each missing field gets its entry.
            AcceptInvitationRequest empty = AcceptInvitationRequest.builder().build();
            errors = new BeanPropertyBindingResult(empty, bindingResult.getObjectName());
            new SpringValidatorAdapter(validator).validate(empty, errors);
        }
        if (errors.hasErrors()) {
            MethodParameter body = new MethodParameter(ReflectionUtils.findMethod(AuthController.class,
                    "acceptInvitation", String.class, AcceptInvitationRequest.class, BindingResult.class,
                    UserPrincipal.class), 1);
            throw new MethodArgumentNotValidException(body, errors);
        }
        return new ResponseEntity<>(invitationService.acceptInvitationAsNewUser(token, request), HttpStatus.CREATED);
    }

}
