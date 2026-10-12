package com.example.authservice.security;

import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.OrgMembership;
import com.example.authservice.repository.OrgMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D3: the guard reads the caller from the UserPrincipal and the role from the database, and compares by rank.
 */
class OrgGuardTest {

    private static final UUID USER_ID = UUID.fromString("7f1c2a9e-0000-4000-8000-000000000001");
    private static final UUID ORG_ID = UUID.fromString("7f1c2a9e-0000-4000-8000-0000000000a1");
    private static final UUID OTHER_ORG_ID = UUID.fromString("7f1c2a9e-0000-4000-8000-0000000000a2");

    private final OrgMembershipRepository repository = mock(OrgMembershipRepository.class);
    private final OrgGuard guard = new OrgGuard(repository);
    private final Authentication caller = authenticated(USER_ID);

    @ParameterizedTest(name = "a {0} passes a {1} check: {2}")
    @CsvSource({
            "OWNER,  OWNER,  true", "OWNER,  ADMIN,  true", "OWNER,  MEMBER, true", "OWNER,  READER, true",
            "ADMIN,  OWNER,  false", "ADMIN,  ADMIN,  true", "ADMIN,  MEMBER, true", "ADMIN,  READER, true",
            "MEMBER, OWNER,  false", "MEMBER, ADMIN,  false", "MEMBER, MEMBER, true", "MEMBER, READER, true",
            "READER, OWNER,  false", "READER, ADMIN,  false", "READER, MEMBER, false", "READER, READER, true"
    })
    void eachRoleAgainstEachRequiredRole(OrganizationRole callerRole, String requiredRole, boolean expected) {
        givenMembership(callerRole);

        assertThat(guard.hasRole(ORG_ID, caller, requiredRole)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "a {0} is a member")
    @EnumSource(OrganizationRole.class)
    void everyRoleIsAMember(OrganizationRole callerRole) {
        givenMembership(callerRole);

        assertThat(guard.isMember(ORG_ID, caller)).isTrue();
    }

    @Test
    void aNonMemberIsDenied() {
        when(repository.findByUserIdAndOrganizationId(USER_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThat(guard.hasRole(ORG_ID, caller, "READER")).isFalse();
        assertThat(guard.isMember(ORG_ID, caller)).isFalse();
    }

    @Test
    void anUnknownOrganizationIsDeniedLikeANonMember() {
        givenMembership(OrganizationRole.OWNER);
        when(repository.findByUserIdAndOrganizationId(USER_ID, OTHER_ORG_ID)).thenReturn(Optional.empty());

        assertThat(guard.hasRole(OTHER_ORG_ID, caller, "READER")).isFalse();
        assertThat(guard.isMember(OTHER_ORG_ID, caller)).isFalse();
    }

    @Test
    void theRoleNameIsCaseInsensitive() {
        givenMembership(OrganizationRole.ADMIN);

        assertThat(guard.hasRole(ORG_ID, caller, "admin")).isTrue();
    }

    @ParameterizedTest(name = "unknown role \"{0}\" fails closed")
    @NullAndEmptySource
    @ValueSource(strings = {"SUPERUSER", "TESTER", " ADMIN"})
    void anUnknownRoleStringFailsClosed(String role) {
        givenMembership(OrganizationRole.OWNER);

        assertThat(guard.hasRole(ORG_ID, caller, role)).isFalse();
    }

    @Test
    void callersWithoutAUserPrincipalAreDeniedWithoutALookup() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        Authentication otherPrincipal = new UsernamePasswordAuthenticationToken("someone", null, List.of());
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(principal(USER_ID), null);

        assertThat(guard.hasRole(ORG_ID, null, "READER")).isFalse();
        assertThat(guard.hasRole(ORG_ID, otherPrincipal, "READER")).isFalse();
        assertThat(guard.hasRole(ORG_ID, unauthenticated, "READER")).isFalse();
        assertThat(guard.isMember(ORG_ID, anonymous)).isFalse();
        assertThat(guard.isMember(null, caller)).isFalse();
        verify(repository, never()).findByUserIdAndOrganizationId(any(), any());
    }

    @Test
    void theRoleIsReadFromTheDatabaseOnEveryCall() {
        givenMembership(OrganizationRole.ADMIN);
        assertThat(guard.hasRole(ORG_ID, caller, "ADMIN")).isTrue();

        givenMembership(OrganizationRole.MEMBER);
        assertThat(guard.hasRole(ORG_ID, caller, "ADMIN")).isFalse();
    }

    private void givenMembership(OrganizationRole role) {
        OrgMembership membership = new OrgMembership();
        membership.setRole(role);
        when(repository.findByUserIdAndOrganizationId(USER_ID, ORG_ID)).thenReturn(Optional.of(membership));
    }

    private static UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, "ana@example.com", "hash", List.of(), UserStatus.PENDING_VERIFICATION);
    }

    private static Authentication authenticated(UUID userId) {
        UserPrincipal principal = principal(userId);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
