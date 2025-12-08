package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "availability_game_preferences")
@Data
@NoArgsConstructor
public class AvailabilityGamePreference {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn(name = "availability_slot_id", nullable = false)
    private AvailabilitySlot availabilitySlot;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    private int weight; // 0-10
}
