package com.example.authservice.repository;

import com.example.authservice.enums.Plan;
import com.example.authservice.model.Organization;
import com.example.authservice.model.Project;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E31: {@code Project} is usable through its accessors and builder, round-trips {@code archivedAt}, and no longer
 * has a {@code secret_token} column (D8).
 */
@DataJpaTest
class ProjectRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 13, 9, 30, 0);
    private static final LocalDateTime ARCHIVED_AT = LocalDateTime.of(2026, 10, 13, 10, 15, 30, 123_456_000);

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aProjectWithArchivedAtRoundTrips() {
        // Given
        Organization acme = acme();
        Project saved = projectRepository.save(Project.builder()
                .organization(acme).name("Checkout").createdAt(CREATED_AT).archivedAt(ARCHIVED_AT).build());
        entityManager.flush();
        entityManager.clear();

        // When
        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getOrganization().getId()).isEqualTo(acme.getId());
        assertThat(loaded.getName()).isEqualTo("Checkout");
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(loaded.getArchivedAt()).isEqualTo(ARCHIVED_AT);
    }

    @Test
    void anActiveProjectHasNoArchivedAt() {
        // Given
        Project saved = projectRepository.save(Project.builder()
                .organization(acme()).name("Billing").createdAt(CREATED_AT).build());
        entityManager.flush();
        entityManager.clear();

        // When
        Project loaded = projectRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getArchivedAt()).isNull();
    }

    @Test
    void theProjectsTableHasArchivedAtAndNoSecretToken() {
        // When
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'projects'",
                String.class);

        // Then
        assertThat(columns).containsExactlyInAnyOrder("id", "org_id", "name", "created_at", "archived_at");
    }

    private Organization acme() {
        return organizationRepository.save(Organization.builder()
                .name("Acme QA").plan(Plan.FREE).seatLimit(5).createdAt(CREATED_AT).build());
    }
}
