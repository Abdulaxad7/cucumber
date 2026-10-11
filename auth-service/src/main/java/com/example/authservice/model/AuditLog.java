package com.example.authservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    private String action;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Column(columnDefinition = "json")
    private String metadata;

    private LocalDateTime createdAt;

    // Getters and Setters
}
