package com.example.authservice.dto.response;

import com.example.authservice.enums.Plan;
import lombok.Builder;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@NonNull
public record OrganizationResponse(
        UUID id,
        String name,
        Plan plan,
        Integer seatLimit,
        LocalDateTime createdAt
) {
}
