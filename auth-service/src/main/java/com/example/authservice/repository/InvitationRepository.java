package com.example.authservice.repository;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.model.Invitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findLockedByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findLockedByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEmailAndStatusAndExpiresAtAfter(UUID organizationId, String email,
                                                                     InvitationStatus status, LocalDateTime now);

    long countByOrganizationIdAndStatusAndExpiresAtAfter(UUID organizationId, InvitationStatus status,
                                                         LocalDateTime now);

    List<Invitation> findByOrganizationIdAndStatusAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
            UUID organizationId, InvitationStatus status, LocalDateTime now);
}
