package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "game_session_players")
@Data
@NoArgsConstructor
public class GameSessionPlayer {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private SessionPlayerStatus status = SessionPlayerStatus.PENDING;

    private String rejectionReason;

    public enum SessionPlayerStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
