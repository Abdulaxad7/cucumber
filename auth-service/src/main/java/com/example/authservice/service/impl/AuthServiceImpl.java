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
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** BCrypt reads only the first 72 bytes of a password; no account can have a longer one (E17). */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest registerRequest) {
        String email = normalizeEmail(registerRequest.email());
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
            // AC-2.11: flush now, so a concurrent registration of the same email fails here and not at commit.
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
        String email = normalizeEmail(loginRequest.email());
        // AC-2.10: BCrypt would ignore the bytes after the 72nd, so a longer password can never be right.
        if (loginRequest.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadCredentialsException("Bad credentials");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        loginRequest.password()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = tokenProvider.generateToken(user);
        return new LoginResponse(token);
    }

    /**
     * Whether Hibernate classified the violation as a unique constraint (from the dialect's SQLState and error code,
     * not from the message). The users table has no unique constraint besides the generated id and the email, so on
     * this insert it is the email.
     */
    private static boolean isUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getKind() == ConstraintKind.UNIQUE;
            }
        }
        return false;
    }

    /** D12: emails are trimmed and lower-cased before they're stored or compared; validation runs on the raw input. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
