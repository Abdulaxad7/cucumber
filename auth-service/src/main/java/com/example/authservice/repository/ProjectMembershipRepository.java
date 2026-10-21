package com.example.authservice.repository;

import com.example.authservice.model.ProjectMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByUserIdAndProjectId(UUID userId, UUID projectId);

    List<ProjectMembership> findByProjectIdOrderByUserEmailAsc(UUID projectId);

    @Modifying
    @Query("""
            delete from ProjectMembership m where m.user.id = :userId
            and m.project.id in (select p.id from Project p where p.organization.id = :orgId)""")
    int deleteByUserIdInOrganization(@Param("userId") UUID userId, @Param("orgId") UUID orgId);

    @Modifying
    @Query("""
            update ProjectMembership m set m.role = com.example.authservice.enums.ProjectRole.READER
            where m.user.id = :userId and m.role <> com.example.authservice.enums.ProjectRole.READER
            and m.project.id in (select p.id from Project p where p.organization.id = :orgId)""")
    int lowerRolesToReaderInOrganization(@Param("userId") UUID userId, @Param("orgId") UUID orgId);
}
