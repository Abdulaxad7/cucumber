package com.example.authservice.security;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private UUID id;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private UserStatus status;

    public UserPrincipal(UUID id, String email, String password, Collection<? extends GrantedAuthority> authorities,
                         UserStatus status) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.status = status;
    }

    public static UserPrincipal create(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList(),
                user.getStatus()
        );
    }

    public UUID getId() {
        return id;
    }

    public UserStatus getStatus() {
        return status;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.SUSPENDED;
    }
}
