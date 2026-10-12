package com.example.authservice.security;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D4: only suspended users are disabled in phase 1.
 */
class UserPrincipalTest {

    @ParameterizedTest(name = "{0} enabled = {1}")
    @CsvSource({"ACTIVE, true", "PENDING_VERIFICATION, true", "SUSPENDED, false"})
    @Tag("AC-2.6")
    @Tag("AC-2.7")
    @Tag("AC-3.4")
    void onlySuspendedUsersAreDisabled(UserStatus status, boolean enabled) {
        User user = User.builder().id(UUID.randomUUID()).email("ana@example.com").passwordHash("hash").status(status).build();

        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.isEnabled()).isEqualTo(enabled);
        assertThat(principal.getStatus()).isEqualTo(status);
    }
}
