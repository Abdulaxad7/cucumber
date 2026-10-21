package com.example.authservice.service.impl;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E32 (D3): the effective project role is none without an organization membership, ADMIN for an organization OWNER
 * or ADMIN, and otherwise the explicit role, capped at READER for an organization READER.
 */
class ProjectRoleServiceTest {

    private static final UUID USER_ID = UUID.fromString("5d0e7b3a-0000-4000-8000-000000000001");
    private static final UUID ORG_ID = UUID.fromString("5d0e7b3a-0000-4000-8000-0000000000a1");
    private static final UUID OTHER_ORG_ID = UUID.fromString("5d0e7b3a-0000-4000-8000-0000000000a2");
    private static final UUID PROJECT_ID = UUID.fromString("5d0e7b3a-0000-4000-8000-0000000000b1");

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final OrgMembershipRepository orgMembershipRepository = mock(OrgMembershipRepository.class);
    private final ProjectMembershipRepository projectMembershipRepository = mock(ProjectMembershipRepository.class);
    private final ProjectRoleService service =
            new ProjectRoleService(projectRepository, orgMembershipRepository, projectMembershipRepository);

    @ParameterizedTest(name = "organization {0}, explicit {1}: effective {2}")
    @CsvSource({
            "OWNER,  ADMIN,  ADMIN", "OWNER,  MEMBER, ADMIN", "OWNER,  TESTER, ADMIN", "OWNER,  READER, ADMIN",
            "OWNER,  NONE,   ADMIN",
            "ADMIN,  ADMIN,  ADMIN", "ADMIN,  MEMBER, ADMIN", "ADMIN,  TESTER, ADMIN", "ADMIN,  READER, ADMIN",
            "ADMIN,  NONE,   ADMIN",
            "MEMBER, ADMIN,  ADMIN", "MEMBER, MEMBER, MEMBER", "MEMBER, TESTER, TESTER", "MEMBER, READER, READER",
            "MEMBER, NONE,   NONE",
            "READER, ADMIN,  READER", "READER, MEMBER, READER", "READER, TESTER, READER", "READER, READER, READER",
            "READER, NONE,   NONE",
            "NONE,   ADMIN,  NONE", "NONE,   MEMBER, NONE", "NONE,   TESTER, NONE", "NONE,   READER, NONE",
            "NONE,   NONE,   NONE"
    })
    void everyOrganizationRoleAgainstEveryExplicitRole(String orgRole, String explicitRole, String effective) {
        // Given
        givenProjectIn(ORG_ID);
        givenOrganizationRole(orgRole);
        givenExplicitRole(explicitRole);

        // When
        Optional<ProjectRole> role = service.getRoleForProject(USER_ID, PROJECT_ID);

        // Then
        assertThat(role).isEqualTo(effective.equals("NONE") ? Optional.empty() : Optional.of(ProjectRole.valueOf(effective)));
    }

    @Test
    void anUnknownProjectHasNoRoleAndNeedsNoMembershipLookup() {
        // Given
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThat(service.getRoleForProject(USER_ID, PROJECT_ID)).isEmpty();
        assertThat(service.getRoleForProject(USER_ID, ORG_ID, PROJECT_ID)).isEmpty();
        verify(orgMembershipRepository, never()).findByUserIdAndOrganizationId(any(), any());
        verify(projectMembershipRepository, never()).findByUserIdAndProjectId(any(), any());
    }

    @Test
    void aProjectOfAnotherOrganizationHasNoRoleThere() {
        // Given
        givenProjectIn(ORG_ID);
        givenOrganizationRole("OWNER");

        // When / Then
        assertThat(service.getRoleForProject(USER_ID, ORG_ID, PROJECT_ID)).contains(ProjectRole.ADMIN);
        assertThat(service.getRoleForProject(USER_ID, OTHER_ORG_ID, PROJECT_ID)).isEmpty();
    }

    @Test
    void missingIdsHaveNoRole() {
        assertThat(service.getRoleForProject(null, PROJECT_ID)).isEmpty();
        assertThat(service.getRoleForProject(USER_ID, null)).isEmpty();
        assertThat(service.getRoleForProject(USER_ID, null, PROJECT_ID)).isEmpty();
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void theRoleIsReadFromTheDatabaseOnEveryCall() {
        // Given
        givenProjectIn(ORG_ID);
        givenOrganizationRole("MEMBER");
        givenExplicitRole("TESTER");
        assertThat(service.getRoleForProject(USER_ID, PROJECT_ID)).contains(ProjectRole.TESTER);

        // When
        givenExplicitRole("NONE");

        // Then
        assertThat(service.getRoleForProject(USER_ID, PROJECT_ID)).isEmpty();
    }

    private void givenProjectIn(UUID orgId) {
        Organization organization = Organization.builder().id(orgId).build();
        Project project = Project.builder().id(PROJECT_ID).organization(organization).name("Checkout").build();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void givenOrganizationRole(String role) {
        Optional<OrgMembership> membership = Optional.empty();
        if (!role.equals("NONE")) {
            OrgMembership orgMembership = new OrgMembership();
            orgMembership.setRole(OrganizationRole.valueOf(role));
            membership = Optional.of(orgMembership);
        }
        when(orgMembershipRepository.findByUserIdAndOrganizationId(USER_ID, ORG_ID)).thenReturn(membership);
    }

    private void givenExplicitRole(String role) {
        Optional<ProjectMembership> membership = role.equals("NONE")
                ? Optional.empty()
                : Optional.of(ProjectMembership.builder().role(ProjectRole.valueOf(role)).build());
        when(projectMembershipRepository.findByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(membership);
    }
}
