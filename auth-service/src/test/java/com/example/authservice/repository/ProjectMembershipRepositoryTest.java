package com.example.authservice.repository;

import com.example.authservice.enums.Plan;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.Organization;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.support.UsesEmptyTestDatabase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E38 and E39 (D6): the bulk queries touch only the user's memberships in the given organization's projects.
 */
@DataJpaTest
@UsesEmptyTestDatabase
class ProjectMembershipRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-13T09:30:00Z");

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Autowired
    private EntityManager entityManager;

    private User hal;
    private User gus;
    private Project checkout;
    private Project billing;
    private Project portal;

    @BeforeEach
    void seed() {
        Organization acme = persist(Organization.builder().name("Acme QA").plan(Plan.FREE).createdAt(NOW).build());
        Organization initech = persist(Organization.builder().name("Initech QA").plan(Plan.FREE).createdAt(NOW).build());
        hal = persist(User.builder().email("hal@example.com").fullName("Hal Ito").status(UserStatus.ACTIVE)
                .createdAt(NOW).updatedAt(NOW).build());
        gus = persist(User.builder().email("gus@example.com").fullName("Gus Hale").status(UserStatus.ACTIVE)
                .createdAt(NOW).updatedAt(NOW).build());
        checkout = persist(Project.builder().organization(acme).name("Checkout").createdAt(NOW).build());
        billing = persist(Project.builder().organization(acme).name("Billing").createdAt(NOW).build());
        portal = persist(Project.builder().organization(initech).name("Portal").createdAt(NOW).build());
        member(checkout, hal, ProjectRole.TESTER);
        member(billing, hal, ProjectRole.ADMIN);
        member(portal, hal, ProjectRole.MEMBER);
        member(checkout, gus, ProjectRole.MEMBER);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Tag("AC-6.1")
    void deletingInAnOrganizationRemovesOnlyThatUsersMembershipsThere() {
        // When
        int deleted = projectMembershipRepository.deleteByUserIdInOrganization(hal.getId(),
                checkout.getOrganization().getId());

        // Then
        assertThat(deleted).isEqualTo(2);
        assertThat(roleOf(hal, checkout)).isEmpty();
        assertThat(roleOf(hal, billing)).isEmpty();
        assertThat(roleOf(hal, portal)).contains(ProjectRole.MEMBER);
        assertThat(roleOf(gus, checkout)).contains(ProjectRole.MEMBER);
    }

    @Test
    @Tag("AC-6.3")
    void loweringInAnOrganizationSetsOnlyThatUsersHigherRolesThereToReader() {
        // When
        int lowered = projectMembershipRepository.lowerRolesToReaderInOrganization(hal.getId(),
                checkout.getOrganization().getId());

        // Then
        assertThat(lowered).isEqualTo(2);
        assertThat(roleOf(hal, checkout)).contains(ProjectRole.READER);
        assertThat(roleOf(hal, billing)).contains(ProjectRole.READER);
        assertThat(roleOf(hal, portal)).contains(ProjectRole.MEMBER);
        assertThat(roleOf(gus, checkout)).contains(ProjectRole.MEMBER);
    }

    private Optional<ProjectRole> roleOf(User user, Project project) {
        entityManager.clear();
        return projectMembershipRepository.findByUserIdAndProjectId(user.getId(), project.getId())
                .map(ProjectMembership::getRole);
    }

    private void member(Project project, User user, ProjectRole role) {
        persist(ProjectMembership.builder().project(project).user(user).role(role).createdAt(NOW).build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
