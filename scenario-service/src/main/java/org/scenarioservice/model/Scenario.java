package org.scenarioservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.scenarioservice.enums.ScenarioKind;

import java.time.Instant;
import java.util.UUID;

/**
 * A Background, Scenario or Scenario Outline of the current version (ERD {@code scenarios}). {@code position} is its
 * index among its container's children (the Feature's, or its Rule's).
 */
@Entity
@Table(name = "scenarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scenario extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID featureId;

    private UUID ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScenarioKind kind;

    @Column(nullable = false, columnDefinition = "text")
    private String keywordText;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int line;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "Scenario[id=" + id + ", kind=" + kind + ", position=" + position + "]";
    }
}
