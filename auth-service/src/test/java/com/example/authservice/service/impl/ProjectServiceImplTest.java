package com.example.authservice.service.impl;

import com.example.authservice.dto.request.AddProjectMemberRequest;
import com.example.authservice.dto.request.CreateProjectRequest;
import com.example.authservice.dto.request.RenameProjectRequest;
import com.example.authservice.dto.response.ProjectResponse;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.ProjectRole;
import com.example.authservice.exception.NotOrgMemberException;
import com.example.authservice.exception.ProjectArchivedException;
import com.example.authservice.exception.ProjectMemberNotFoundException;
import com.example.authservice.exception.ProjectNameTakenException;
import com.example.authservice.exception.ProjectRoleNotAllowedException;
import com.example.authservice.exception.SelfRoleChangeException;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.Organization;
import com.example.authservice.model.Project;
import com.example.authservice.model.ProjectMembership;
import com.example.authservice.model.User;
import com.example.authservice.repository.OrgMembershipRepository;
import com.example.authservice.repository.OrganizationRepository;
import com.example.authservice.repository.ProjectMembershipRepository;
import com.example.authservice.repository.ProjectRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the project rules in {@link ProjectServiceImpl}: names, order, archive, the add, change and remove
 * orders, and the project role cap (D3, D5, D6, A5, P13, P14).
 * Collaborators the tested paths never reach are left unset ({@code null}).
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMembershipRepository projectMembershipRepository;
    @Mock
    private OrgMembershipRepository orgMembershipRepository;
    @Mock
    private ProjectRoleService projectRoleService;
    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private ProjectServiceImpl service;

    private final Organization acme = Organization.builder().id(ORG_ID).name("Acme QA").build();

    @Test
    @Tag("AC-1.4")
    void aNameTakenInAnyLetterCaseOrSpacingIsRejectedAfterTheOrganizationIsLocked() {
        // Given
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(project("Checkout", null)));

        // When / Then
        assertThatThrownBy(() -> service.createProject(ORG_ID, new CreateProjectRequest("  cHeCkOuT "), ACTOR_ID))
                .isInstanceOf(ProjectNameTakenException.class);
        InOrder order = inOrder(organizationRepository, projectRepository);
        order.verify(organizationRepository).findLockedById(ORG_ID);
        order.verify(projectRepository).findByOrganizationId(ORG_ID);
        verify(projectRepository, never()).save(any());
    }

    @Test
    @Tag("AC-1.4")
    void anArchivedProjectKeepsItsName() {
        // Given
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(project("Billing", Instant.now())));

        // When / Then
        assertThatThrownBy(() -> service.createProject(ORG_ID, new CreateProjectRequest("billing"), ACTOR_ID))
                .isInstanceOf(ProjectNameTakenException.class);
    }

    @Test
    @Tag("AC-1.8")
    void theNameIsStoredTrimmedAndMyRoleComesFromTheResolver() {
        // Given
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(projectRoleService.effectiveRole(eq(ACTOR_ID), any(Project.class))).thenReturn(Optional.of(ProjectRole.ADMIN));

        // When
        ProjectResponse created = service.createProject(ORG_ID, new CreateProjectRequest("  Mobile App  "), ACTOR_ID);

        // Then
        assertThat(created.name()).isEqualTo("Mobile App");
        assertThat(created.orgId()).isEqualTo(ORG_ID);
        assertThat(created.archivedAt()).isNull();
        assertThat(created.myRole()).isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    @Tag("AC-2.1")
    void theListHasOnlyProjectsWithAnEffectiveRoleOrderedByNameIgnoringCase() {
        // Given
        Project checkout = project("Checkout", null);
        Project billing = project("billing", null);
        Project portal = project("Admin Portal", null);
        when(projectRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(checkout, portal, billing));
        when(projectRoleService.effectiveRole(ACTOR_ID, checkout)).thenReturn(Optional.of(ProjectRole.TESTER));
        when(projectRoleService.effectiveRole(ACTOR_ID, billing)).thenReturn(Optional.of(ProjectRole.READER));
        when(projectRoleService.effectiveRole(ACTOR_ID, portal)).thenReturn(Optional.empty());

        // When
        List<ProjectResponse> listed = service.listProjects(ORG_ID, ACTOR_ID);

        // Then
        assertThat(listed).extracting(ProjectResponse::name, ProjectResponse::myRole).containsExactly(
                tuple("billing", ProjectRole.READER),
                tuple("Checkout", ProjectRole.TESTER));
    }

    @Test
    @Tag("AC-3.6")
    void renamingAnArchivedProjectIsRejectedBeforeTheNameIsCompared() {
        // Given
        Project checkout = project("Checkout", Instant.now());
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));

        // When / Then
        assertThatThrownBy(() -> service.renameProject(ORG_ID, checkout.getId(), new RenameProjectRequest("Billing"),
                ACTOR_ID)).isInstanceOf(ProjectArchivedException.class);
        verify(projectRepository, never()).findByOrganizationId(any());
        assertThat(checkout.getName()).isEqualTo("Checkout");
    }

    @Test
    @Tag("AC-3.2")
    void aProjectMayTakeItsOwnNameInAnotherCase() {
        // Given
        Project checkout = project("Checkout", null);
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));
        when(projectRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(checkout, project("Billing", null)));
        when(projectRoleService.effectiveRole(ACTOR_ID, checkout)).thenReturn(Optional.of(ProjectRole.ADMIN));

        // When
        ProjectResponse renamed = service.renameProject(ORG_ID, checkout.getId(), new RenameProjectRequest(" CHECKOUT "),
                ACTOR_ID);

        // Then
        assertThat(renamed.name()).isEqualTo("CHECKOUT");
    }

    @Test
    @Tag("AC-3.8")
    void archivingAnArchivedProjectKeepsItsArchiveTime() {
        // Given
        Instant archivedAt = Instant.parse("2026-10-13T09:30:00.123456Z");
        Project checkout = project("Checkout", archivedAt);
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));
        when(projectRoleService.effectiveRole(ACTOR_ID, checkout)).thenReturn(Optional.of(ProjectRole.ADMIN));

        // When
        ProjectResponse archived = service.archiveProject(ORG_ID, checkout.getId(), ACTOR_ID);

        // Then
        assertThat(archived.archivedAt()).isEqualTo(archivedAt);
    }

    @ParameterizedTest(name = "AC-4.6 organization {0} as project {1}: allowed {2}")
    @CsvSource({
            "READER, READER, true", "READER, TESTER, false", "READER, MEMBER, false", "READER, ADMIN, false",
            "MEMBER, READER, true", "MEMBER, TESTER, true", "MEMBER, MEMBER, true", "MEMBER, ADMIN, true",
            "ADMIN, ADMIN, true", "OWNER, ADMIN, true"})
    @Tag("AC-4.6")
    void anExplicitRoleIsCappedByTheOrganizationRole(OrganizationRole orgRole, ProjectRole role, boolean allowed) {
        // Given
        Project checkout = project("Checkout", null);
        User ivy = User.builder().id(UUID.randomUUID()).email("ivy@example.com").fullName("Ivy Moss").build();
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));
        OrgMembership ivyInAcme = new OrgMembership();
        ivyInAcme.setUser(ivy);
        ivyInAcme.setOrganization(acme);
        ivyInAcme.setRole(orgRole);
        when(orgMembershipRepository.findByUserIdAndOrganizationId(ivy.getId(), ORG_ID))
                .thenReturn(Optional.of(ivyInAcme));
        when(projectMembershipRepository.findByUserIdAndProjectId(ivy.getId(), checkout.getId()))
                .thenReturn(Optional.empty());
        AddProjectMemberRequest request = new AddProjectMemberRequest(ivy.getId(), role);

        // When / Then
        if (!allowed) {
            assertThatThrownBy(() -> service.addMember(ORG_ID, checkout.getId(), request, ACTOR_ID))
                    .isInstanceOf(ProjectRoleNotAllowedException.class);
            verify(projectMembershipRepository, never()).save(any());
            return;
        }
        when(projectMembershipRepository.save(any(ProjectMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.addMember(ORG_ID, checkout.getId(), request, ACTOR_ID).role()).isEqualTo(role);
    }

    @Test
    @Tag("AC-3.6")
    void addingToAnArchivedProjectIsRejectedBeforeTheUserIsLookedUp() {
        // Given
        Project checkout = project("Checkout", Instant.now());
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));

        // When / Then
        assertThatThrownBy(() -> service.addMember(ORG_ID, checkout.getId(),
                new AddProjectMemberRequest(UUID.randomUUID(), ProjectRole.READER), ACTOR_ID))
                .isInstanceOf(ProjectArchivedException.class);
        verify(orgMembershipRepository, never()).findByUserIdAndOrganizationId(any(), any());
    }

    @Test
    @Tag("AC-4.4")
    void aUserOutsideTheOrganizationIsNotAnOrgMember() {
        // Given
        Project checkout = project("Checkout", null);
        UUID eve = UUID.randomUUID();
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));
        when(orgMembershipRepository.findByUserIdAndOrganizationId(eve, ORG_ID)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.addMember(ORG_ID, checkout.getId(),
                new AddProjectMemberRequest(eve, ProjectRole.READER), ACTOR_ID))
                .isInstanceOf(NotOrgMemberException.class);
    }

    @Test
    @Tag("AC-5.3")
    void changingTheRoleOfSomeoneWithoutAMembershipIsMemberNotFoundEvenForThemselves() {
        // Given
        Project checkout = activeCheckoutUnderLock();
        when(projectMembershipRepository.findByUserIdAndProjectId(ACTOR_ID, checkout.getId()))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.changeMemberRole(ORG_ID, checkout.getId(), ACTOR_ID, ProjectRole.READER,
                ACTOR_ID)).isInstanceOf(ProjectMemberNotFoundException.class);
    }

    @Test
    @Tag("AC-5.2")
    void changingYourOwnRoleIsSelfRoleChangeBeforeTheCapIsChecked() {
        // Given
        Project checkout = activeCheckoutUnderLock();
        when(projectMembershipRepository.findByUserIdAndProjectId(ACTOR_ID, checkout.getId()))
                .thenReturn(Optional.of(membership(checkout, ProjectRole.READER)));

        // When / Then
        assertThatThrownBy(() -> service.changeMemberRole(ORG_ID, checkout.getId(), ACTOR_ID, ProjectRole.TESTER,
                ACTOR_ID)).isInstanceOf(SelfRoleChangeException.class);
        verify(orgMembershipRepository, never()).findByUserIdAndOrganizationId(any(), any());
    }

    @Test
    @Tag("AC-5.6")
    void removingSomeoneElseAfterADemotionInBetweenGetsTheGuardsAnswer() {
        // Given
        Project checkout = activeCheckoutUnderLock();
        UUID hal = UUID.randomUUID();
        when(projectMembershipRepository.findByUserIdAndProjectId(hal, checkout.getId()))
                .thenReturn(Optional.of(membership(checkout, ProjectRole.TESTER)));
        when(projectRoleService.effectiveRole(ACTOR_ID, checkout)).thenReturn(Optional.of(ProjectRole.MEMBER));

        // When / Then
        assertThatThrownBy(() -> service.removeMember(ORG_ID, checkout.getId(), hal, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectMembershipRepository, never()).delete(any());
    }

    @Test
    @Tag("AC-5.7")
    void anyExplicitMemberCanLeaveWithoutBeingAnAdmin() {
        // Given
        Project checkout = activeCheckoutUnderLock();
        ProjectMembership own = membership(checkout, ProjectRole.READER);
        when(projectMembershipRepository.findByUserIdAndProjectId(ACTOR_ID, checkout.getId()))
                .thenReturn(Optional.of(own));

        // When
        service.removeMember(ORG_ID, checkout.getId(), ACTOR_ID, ACTOR_ID);

        // Then
        verify(projectMembershipRepository).delete(own);
        verify(projectRoleService, never()).effectiveRole(any(), any());
    }

    private Project activeCheckoutUnderLock() {
        Project checkout = project("Checkout", null);
        when(organizationRepository.findLockedById(ORG_ID)).thenReturn(Optional.of(acme));
        when(projectRepository.findByIdAndOrganizationId(checkout.getId(), ORG_ID)).thenReturn(Optional.of(checkout));
        return checkout;
    }

    private static ProjectMembership membership(Project project, ProjectRole role) {
        return ProjectMembership.builder().id(UUID.randomUUID()).project(project).role(role).build();
    }

    private Project project(String name, Instant archivedAt) {
        return Project.builder().id(UUID.randomUUID()).organization(acme).name(name)
                .createdAt(Instant.now()).archivedAt(archivedAt).build();
    }
}
