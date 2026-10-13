package com.example.authservice.repository;

import com.example.authservice.enums.InvitationStatus;
import com.example.authservice.enums.OrganizationRole;
import com.example.authservice.enums.Plan;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.Invitation;
import com.example.authservice.model.Organization;
import com.example.authservice.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E37 (D7): an invitation's role is an {@link OrganizationRole}, stored by name, so stored values don't change.
 */
@DataJpaTest
class InvitationRoleRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 13, 9, 30, 0);

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theRoleIsReadBackAsAnOrganizationRoleAndStoredByName() {
        // Given
        Organization acme = organizationRepository.save(Organization.builder()
                .name("Acme QA").plan(Plan.FREE).seatLimit(5).createdAt(CREATED_AT).build());
        User ben = userRepository.save(User.builder()
                .email("ben@example.com").passwordHash("hash").fullName("Ben Ode")
                .status(UserStatus.ACTIVE).createdAt(CREATED_AT).build());
        Invitation saved = invitationRepository.save(Invitation.builder()
                .email("fay@example.com")
                .organization(acme)
                .role(OrganizationRole.ADMIN)
                .invitedBy(ben)
                .token("inv-role")
                .status(InvitationStatus.PENDING)
                .createdAt(CREATED_AT)
                .expiresAt(CREATED_AT.plusDays(7))
                .build());
        entityManager.flush();
        entityManager.clear();

        // When
        Invitation loaded = invitationRepository.findById(saved.getId()).orElseThrow();
        String stored = jdbcTemplate.queryForObject("SELECT CAST(role AS VARCHAR) FROM invitations WHERE id = ?",
                String.class, saved.getId());

        // Then
        assertThat(loaded.getRole()).isEqualTo(OrganizationRole.ADMIN);
        assertThat(stored).isEqualTo("ADMIN");
    }
}
