package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GameDto {
    private String id;
    private String title;
    private int minPlayers;
    private int maxPlayers;
    private String genre;
    private String coverImageUrl;
    private LocalDateTime createdAt;
}
