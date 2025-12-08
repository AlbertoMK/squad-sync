package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "games")
@Data
@NoArgsConstructor
public class Game {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @Column(nullable = false)
    private String title;

    private int minPlayers = 1;
    private int maxPlayers = 10;
    private String genre;
    private String coverImageUrl;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL)
    private List<UserGamePreference> preferences;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL)
    private List<GameSession> sessions;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL)
    private List<AvailabilitySlot> availabilitySlots;
}
