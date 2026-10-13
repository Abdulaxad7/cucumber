package com.example.authservice.dto.request;

import com.example.authservice.enums.OrganizationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 254, message = "Email must be at most 254 characters long") String email,
        @NotNull OrganizationRole role
) {
}
