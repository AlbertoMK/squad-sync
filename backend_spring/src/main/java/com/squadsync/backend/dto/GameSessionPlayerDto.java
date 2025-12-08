package com.squadsync.backend.dto;

import lombok.Data;

@Data
public class GameSessionPlayerDto {
    private String userId;
    private String username;
    private String avatarColor;
    private String status; // PENDING, ACCEPTED, REJECTED
}
