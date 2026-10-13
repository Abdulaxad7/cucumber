package com.example.authservice.model;

import com.example.authservice.enums.Plan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Plan plan;

    private Integer seatLimit;

    private LocalDateTime createdAt;

}
