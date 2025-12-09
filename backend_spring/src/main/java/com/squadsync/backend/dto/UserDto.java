package com.squadsync.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDto {
    private String id;
    private String username;
    private String email;
    private String discordId;
    private String role;
    private String avatarColor;
    private LocalDateTime createdAt;
}
