package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_sessions")
@Data
@NoArgsConstructor
public class GameSession {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<GameSessionPlayer> players = new java.util.ArrayList<>();

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.PRELIMINARY;

    private double sessionScore;

    @Column(nullable = false)
    private boolean notified = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SessionStatus {
        PRELIMINARY,
        CONFIRMED,
        CANCELLED
    }
}
