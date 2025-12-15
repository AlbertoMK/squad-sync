package com.squadsync.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import com.squadsync.backend.util.DateUtils;

@Entity
@Table(name = "availability_slots")
@Data
@NoArgsConstructor
public class AvailabilitySlot {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @ManyToOne
    @JoinColumn(name = "game_id")
    private Game game; // Optional specific game

    @OneToMany(mappedBy = "availabilitySlot", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<AvailabilityGamePreference> preferences = new java.util.ArrayList<>();

    private LocalDateTime createdAt = DateUtils.now();
}
