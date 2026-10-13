package com.example.authservice.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3 (E30): ADMIN > MEMBER > TESTER > READER, by explicit rank, never by declaration order.
 */
class ProjectRoleTest {

    @ParameterizedTest(name = "{0} is at least {1}: {2}")
    @CsvSource({
            "ADMIN,  ADMIN,  true",
            "ADMIN,  MEMBER, true",
            "ADMIN,  TESTER, true",
            "ADMIN,  READER, true",
            "MEMBER, ADMIN,  false",
            "MEMBER, MEMBER, true",
            "MEMBER, TESTER, true",
            "MEMBER, READER, true",
            "TESTER, ADMIN,  false",
            "TESTER, MEMBER, false",
            "TESTER, TESTER, true",
            "TESTER, READER, true",
            "READER, ADMIN,  false",
            "READER, MEMBER, false",
            "READER, TESTER, false",
            "READER, READER, true"
    })
    void everyPairOfRolesComparesByRank(ProjectRole role, ProjectRole required, boolean expected) {
        assertThat(role.isAtLeast(required)).isEqualTo(expected);
    }

    @Test
    void theRanksAreTheSpecifiedOnes() {
        assertThat(ProjectRole.ADMIN.getRank()).isEqualTo(4);
        assertThat(ProjectRole.MEMBER.getRank()).isEqualTo(3);
        assertThat(ProjectRole.TESTER.getRank()).isEqualTo(2);
        assertThat(ProjectRole.READER.getRank()).isEqualTo(1);
    }

    @Test
    void theConstantsAndTheirOrderAreUnchanged() {
        assertThat(ProjectRole.values()).containsExactly(
                ProjectRole.ADMIN, ProjectRole.MEMBER, ProjectRole.TESTER, ProjectRole.READER);
    }
}
