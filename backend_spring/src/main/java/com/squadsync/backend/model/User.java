package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String discordUsername;

    @Column(nullable = false)
    private String passwordHash;

    private String avatarColor = "#3b82f6";

    private String role;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserGamePreference> preferences;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<AvailabilitySlot> availabilitySlots;
}
