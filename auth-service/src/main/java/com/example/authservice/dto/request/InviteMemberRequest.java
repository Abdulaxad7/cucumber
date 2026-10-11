package com.example.authservice.dto.request;

import com.example.authservice.enums.OrganizationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record InviteMemberRequest(
        @NotBlank @Email String email,
        @NotNull OrganizationRole role
) {
}
