package com.example.authservice.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3: OWNER > ADMIN > MEMBER > READER, by explicit rank, never by declaration order.
 */
class OrganizationRoleTest {

    @ParameterizedTest(name = "{0} is at least {1}: {2}")
    @CsvSource({
            "OWNER,  OWNER,  true",
            "OWNER,  ADMIN,  true",
            "OWNER,  MEMBER, true",
            "OWNER,  READER, true",
            "ADMIN,  OWNER,  false",
            "ADMIN,  ADMIN,  true",
            "ADMIN,  MEMBER, true",
            "ADMIN,  READER, true",
            "MEMBER, OWNER,  false",
            "MEMBER, ADMIN,  false",
            "MEMBER, MEMBER, true",
            "MEMBER, READER, true",
            "READER, OWNER,  false",
            "READER, ADMIN,  false",
            "READER, MEMBER, false",
            "READER, READER, true"
    })
    void everyPairOfRolesComparesByRank(OrganizationRole role, OrganizationRole required, boolean expected) {
        assertThat(role.isAtLeast(required)).isEqualTo(expected);
    }

    @Test
    void theRanksAreTheSpecifiedOnes() {
        assertThat(OrganizationRole.OWNER.getRank()).isEqualTo(4);
        assertThat(OrganizationRole.ADMIN.getRank()).isEqualTo(3);
        assertThat(OrganizationRole.MEMBER.getRank()).isEqualTo(2);
        assertThat(OrganizationRole.READER.getRank()).isEqualTo(1);
    }

    @Test
    void theConstantsAndTheirOrderAreUnchanged() {
        assertThat(OrganizationRole.values()).containsExactly(
                OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.MEMBER, OrganizationRole.READER);
    }
}
