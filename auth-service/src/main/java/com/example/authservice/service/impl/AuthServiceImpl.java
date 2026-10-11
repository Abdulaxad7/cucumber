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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse register(RegisterRequest registerRequest) {
        userRepository.findByEmail(registerRequest.email()).ifPresent(u -> {
            throw new EmailAlreadyExistsException("Email already exists: " + registerRequest.email());
        });

        User user = new User();
        user.setEmail(registerRequest.email());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.setFullName(registerRequest.fullName());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getFullName(),
                savedUser.getStatus(), savedUser.isMfaEnabled(), savedUser.getCreatedAt());
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = tokenProvider.generateToken(user);
        return new LoginResponse(token);
    }
}
