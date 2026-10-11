package com.example.authservice.repository;

import com.example.authservice.model.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByUserIdAndProjectId(UUID userId, UUID projectId);
}
