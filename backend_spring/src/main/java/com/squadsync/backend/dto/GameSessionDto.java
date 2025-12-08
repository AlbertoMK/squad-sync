package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GameSessionDto {
    private String id;
    private String gameId;
    private GameDto game;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<String> playerIds;
    private List<GameSessionPlayerDto> players; // Expanded player details with status
    private double sessionScore;
    private LocalDateTime createdAt;
    private String status;
}
