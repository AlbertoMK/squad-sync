package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "user_game_preferences", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "game_id" })
})
@Data
@NoArgsConstructor
public class UserGamePreference {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    private int weight = 5; // 1-10 scale
}
