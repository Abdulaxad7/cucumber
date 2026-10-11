package com.example.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateOrganizationRequest(
    @NotBlank(message = "Organization name cannot be blank")
    String name
) {}
