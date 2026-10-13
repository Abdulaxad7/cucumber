package com.example.authservice.model;

import com.example.authservice.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true)
    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private boolean mfaEnabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
