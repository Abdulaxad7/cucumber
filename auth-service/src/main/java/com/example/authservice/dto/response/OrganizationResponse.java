package com.example.authservice.dto.response;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.Plan;
import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

@NonNull
public record OrganizationResponse(
        UUID id,
        String name,
        Plan plan,
        Integer seatLimit,
        Instant createdAt,
        OrganizationRole myRole
) {
}
