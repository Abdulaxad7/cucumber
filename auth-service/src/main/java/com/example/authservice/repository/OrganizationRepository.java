package com.example.authservice.repository;

import com.example.authservice.model.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Organization> findLockedById(UUID id);
}
