package com.example.authservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    private String name;

    private LocalDateTime createdAt;

    private LocalDateTime archivedAt;

}
