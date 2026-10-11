package com.example.authservice.model;

import com.example.authservice.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String email;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    private String role; // Can be either OrgRole or ProjectRole

    @ManyToOne
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Column(unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    // Getters and Setters
}

