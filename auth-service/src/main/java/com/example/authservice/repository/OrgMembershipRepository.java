package com.example.authservice.repository;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgMembershipRepository extends JpaRepository<OrgMembership, UUID> {
    List<OrgMembership> findByUser(User user);

    Optional<OrgMembership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrgMembership> findByUserIdOrderByOrganizationNameAscOrganizationIdAsc(UUID userId);

    Long countByOrganizationId(UUID organizationId);

    Boolean existsByOrganizationIdAndUserEmail(UUID organizationId, String email);

    List<OrgMembership> findByOrganizationIdOrderByUserEmailAsc(UUID organizationId);

    Long countByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);
}
