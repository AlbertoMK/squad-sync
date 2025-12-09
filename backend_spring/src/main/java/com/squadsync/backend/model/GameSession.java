package com.squadsync.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private List<GameSessionPlayer> players = new ArrayList<>();

    private double sessionScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus notificationStatus = NotificationStatus.NONE;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SessionStatus {
        PRELIMINARY,
        CONFIRMED,
        CANCELLED
    }

    public enum NotificationStatus {
        NONE,
        PRELIMINARY_SENT,
        CONFIRMED_SENT
    }
}
