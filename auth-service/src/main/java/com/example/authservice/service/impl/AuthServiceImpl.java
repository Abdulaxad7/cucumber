package com.example.authservice.service.impl;

import com.example.authservice.dto.request.LoginRequest;
import com.example.authservice.dto.response.LoginResponse;
import com.example.authservice.dto.request.RegisterRequest;
import com.example.authservice.dto.response.UserResponse;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.AuthService;
import com.example.authservice.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest registerRequest) {
        String email = EmailNormalizer.normalize(registerRequest.email());
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new EmailAlreadyExistsException("Email already exists: " + email);
        });

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.setFullName(registerRequest.fullName());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new EmailAlreadyExistsException("Email already exists: " + email);
            }
            throw ex;
        }

        return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getFullName(),
                savedUser.getStatus(), savedUser.isMfaEnabled(), savedUser.getCreatedAt());
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String email = EmailNormalizer.normalize(loginRequest.email());
        if (loginRequest.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadCredentialsException("Bad credentials");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        loginRequest.password()
                )
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = tokenProvider.generateToken(user);
        return new LoginResponse(token);
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getKind() == ConstraintKind.UNIQUE;
            }
        }
        return false;
    }
}
